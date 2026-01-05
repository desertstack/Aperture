package io.aperture.interceptor

import android.util.Log
import io.aperture.ApertureConfig
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.util.BodyEncoder
import io.aperture.util.HeadersSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import java.io.IOException

/**
 * OkHttp Interceptor that captures HTTP transactions and supports mocking
 * Implements FR-INT-001 through FR-INT-010
 */
class ApertureInterceptor(
    private val repository: TransactionRepository,
    private val config: ApertureConfig
) : Interceptor {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val tag = "ApertureInterceptor"

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Create initial transaction record
        val transaction = HttpTransaction(
            requestDate = System.currentTimeMillis(),
            method = request.method,
            url = request.url.toString(),
            host = request.url.host,
            path = request.url.encodedPath +
                   (if (request.url.encodedQuery != null) "?${request.url.encodedQuery}" else ""),
            scheme = request.url.scheme,
            protocol = null, // Will be set from response
            requestContentType = request.body?.contentType()?.toString(),
            requestContentLength = request.body?.contentLength()?.takeIf { it != -1L },
            requestHeaders = HeadersSerializer.serialize(request.headers, config.headersToRedact),
            requestBody = null, // Will be populated below
            requestBodyIsPlainText = true,
            requestPayloadSize = request.body?.contentLength()?.takeIf { it != -1L }
        )

        // Capture request body (FR-INT-003)
        var updatedTransaction = captureRequestBody(transaction, request)

        // Check for mock response (FR-INT-005, FR-INT-006)
        try {
            val mock = getMockResponse(request.url.toString())
            if (mock != null && mock.mockEnabled) {
                Log.d(tag, "Returning mocked response for: ${request.url}")
                return createMockedResponse(chain, request, mock, updatedTransaction)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking for mock response", e)
            // Continue with real request even if mock check fails (FR-INT-007)
        }

        // Execute real network request
        val startTime = System.currentTimeMillis()
        val response: Response

        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            // Handle network errors (FR-INT-007)
            val endTime = System.currentTimeMillis()
            updatedTransaction = updatedTransaction.copy(
                responseDate = endTime,
                duration = endTime - startTime,
                error = e.message ?: "Network error"
            )
            saveTransactionAsync(updatedTransaction)
            throw e
        }

        // Capture response (FR-INT-004)
        return captureResponse(updatedTransaction, response, startTime)
    }

    /**
     * Capture request body data
     */
    private fun captureRequestBody(
        transaction: HttpTransaction,
        request: okhttp3.Request
    ): HttpTransaction {
        val requestBody = request.body ?: return transaction

        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)

            val (bodyString, isPlainText) = BodyEncoder.encodeBody(
                buffer = buffer,
                contentType = requestBody.contentType()?.toString(),
                maxSize = config.maxBodySize
            )

            transaction.copy(
                requestBody = bodyString,
                requestBodyIsPlainText = isPlainText,
                requestPayloadSize = buffer.size
            )
        } catch (e: Exception) {
            Log.e(tag, "Error capturing request body", e)
            transaction
        }
    }

    /**
     * Capture response data and update transaction
     */
    private fun captureResponse(
        transaction: HttpTransaction,
        response: Response,
        startTime: Long
    ): Response {
        val endTime = System.currentTimeMillis()
        val responseBody = response.body

        // Basic response metadata
        var updatedTransaction = transaction.copy(
            responseDate = endTime,
            duration = endTime - startTime,
            protocol = response.protocol.toString(),
            responseCode = response.code,
            responseMessage = response.message,
            responseContentType = responseBody?.contentType()?.toString(),
            responseContentLength = responseBody?.contentLength()?.takeIf { it != -1L },
            responseHeaders = HeadersSerializer.serialize(response.headers, config.headersToRedact)
        )

        // Capture response body if present
        if (responseBody != null) {
            return try {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE) // Buffer the entire body

                var buffer = source.buffer.clone()

                // Handle Gzip encoding (FR-INT-009)
                val isGzipEncoded = response.header("Content-Encoding")?.equals("gzip", ignoreCase = true) == true
                if (isGzipEncoded) {
                    try {
                        val gzipSource = GzipSource(buffer.clone())
                        val decompressedBuffer = Buffer()
                        decompressedBuffer.writeAll(gzipSource)
                        buffer = decompressedBuffer
                    } catch (e: Exception) {
                        Log.e(tag, "Error decompressing gzip response", e)
                    }
                }

                val (bodyString, isPlainText) = BodyEncoder.encodeBody(
                    buffer = buffer,
                    contentType = responseBody.contentType()?.toString(),
                    maxSize = config.maxBodySize
                )

                updatedTransaction = updatedTransaction.copy(
                    responseBody = bodyString,
                    responseBodyIsPlainText = isPlainText,
                    responsePayloadSize = source.buffer.size,
                    isGzipEncoded = isGzipEncoded
                )

                saveTransactionAsync(updatedTransaction)
                response
            } catch (e: Exception) {
                Log.e(tag, "Error capturing response body", e)
                updatedTransaction = updatedTransaction.copy(
                    error = "Failed to capture response: ${e.message}"
                )
                saveTransactionAsync(updatedTransaction)
                response
            }
        } else {
            saveTransactionAsync(updatedTransaction)
            return response
        }
    }

    /**
     * Create a mocked response from stored mock data
     */
    private fun createMockedResponse(
        chain: Interceptor.Chain,
        request: okhttp3.Request,
        mock: HttpTransaction,
        transaction: HttpTransaction
    ): Response {
        val responseCode = mock.mockResponseCode ?: 200
        val responseHeaders = try {
            if (mock.mockResponseHeaders != null) {
                HeadersSerializer.deserialize(mock.mockResponseHeaders)
            } else {
                okhttp3.Headers.Builder().build()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deserializing mock headers", e)
            okhttp3.Headers.Builder().build()
        }

        val responseBody = (mock.mockResponseBody ?: "").toResponseBody(
            responseHeaders["Content-Type"]?.toMediaTypeOrNull()
        )

        // Save transaction record with mock flag
        val endTime = System.currentTimeMillis()
        val mockedTransaction = transaction.copy(
            responseDate = endTime,
            duration = endTime - transaction.requestDate,
            protocol = "HTTP/1.1",
            responseCode = responseCode,
            responseMessage = "Mocked",
            responseContentType = responseHeaders["Content-Type"],
            responseHeaders = HeadersSerializer.serialize(responseHeaders, config.headersToRedact),
            responseBody = mock.mockResponseBody,
            responseBodyIsPlainText = true,
            responsePayloadSize = mock.mockResponseBody?.length?.toLong() ?: 0,
            isMocked = true
        )
        saveTransactionAsync(mockedTransaction)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(responseCode)
            .message("Mocked")
            .headers(responseHeaders)
            .body(responseBody)
            .build()
    }

    /**
     * Get mock response for a URL (synchronous database query)
     */
    private fun getMockResponse(url: String): HttpTransaction? {
        return try {
            // This needs to be synchronous to work in the interceptor
            // Using runBlocking is acceptable here as per OkHttp's design
            kotlinx.coroutines.runBlocking {
                repository.getMockForUrl(url)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting mock response", e)
            null
        }
    }

    /**
     * Save transaction asynchronously (FR-INT-008)
     */
    private fun saveTransactionAsync(transaction: HttpTransaction) {
        scope.launch {
            try {
                repository.insert(transaction)
            } catch (e: Exception) {
                Log.e(tag, "Error saving transaction", e)
                // Don't crash the app if saving fails (FR-INT-007)
            }
        }
    }
}
