package io.aperture.server.dto

import io.aperture.data.entity.HttpTransaction
import io.aperture.data.entity.TransactionSummary
import kotlinx.serialization.Serializable

/**
 * DTOs for API responses
 */

@Serializable
data class TransactionListResponse(
    val transactions: List<TransactionSummaryDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

@Serializable
data class TransactionDto(
    val id: Long,
    val requestDate: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val scheme: String,
    val protocol: String?,
    val requestContentType: String?,
    val requestContentLength: Long?,
    val requestHeaders: String?,
    val requestBody: String?,
    val requestBodyIsPlainText: Boolean,
    val responseDate: Long?,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseContentType: String?,
    val responseContentLength: Long?,
    val responseHeaders: String?,
    val responseBody: String?,
    val responseBodyIsPlainText: Boolean,
    val duration: Long?,
    val error: String?,
    val requestPayloadSize: Long?,
    val responsePayloadSize: Long?,
    val isGzipEncoded: Boolean,
    val isMocked: Boolean,
    val mockEnabled: Boolean,
    val mockResponseCode: Int?,
    val mockResponseHeaders: String?,
    val mockResponseBody: String?,
    val status: String
)

/**
 * A transaction without its bodies and headers
 *
 * The list view and the live stream carry this. A client that needs a body asks for the one
 * transaction it is showing, through GET /api/transactions/{id}.
 */
@Serializable
data class TransactionSummaryDto(
    val id: Long,
    val requestDate: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val scheme: String,
    val protocol: String?,
    val requestContentType: String?,
    val requestContentLength: Long?,
    val requestBodyIsPlainText: Boolean,
    val responseDate: Long?,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseContentType: String?,
    val responseContentLength: Long?,
    val responseBodyIsPlainText: Boolean,
    val duration: Long?,
    val error: String?,
    val requestPayloadSize: Long?,
    val responsePayloadSize: Long?,
    val isGzipEncoded: Boolean,
    val isMocked: Boolean,
    val mockEnabled: Boolean,
    val mockResponseCode: Int?,
    val status: String
)

@Serializable
data class StatsResponse(
    val totalTransactions: Int,
    val mockedTransactions: Int,
    val failedTransactions: Int,
    val averageDuration: Long
)

@Serializable
data class UpdateMockStatusRequest(
    val enabled: Boolean
)

@Serializable
data class UpdateMockResponseRequest(
    val statusCode: Int,
    val headers: Map<String, String>?,
    val body: String?
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

/**
 * Convert HttpTransaction entity to DTO
 */
fun HttpTransaction.toDto(): TransactionDto {
    return TransactionDto(
        id = id,
        requestDate = requestDate,
        method = method,
        url = url,
        host = host,
        path = path,
        scheme = scheme,
        protocol = protocol,
        requestContentType = requestContentType,
        requestContentLength = requestContentLength,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        requestBodyIsPlainText = requestBodyIsPlainText,
        responseDate = responseDate,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseContentType = responseContentType,
        responseContentLength = responseContentLength,
        responseHeaders = responseHeaders,
        responseBody = responseBody,
        responseBodyIsPlainText = responseBodyIsPlainText,
        duration = duration,
        error = error,
        requestPayloadSize = requestPayloadSize,
        responsePayloadSize = responsePayloadSize,
        isGzipEncoded = isGzipEncoded,
        isMocked = isMocked,
        mockEnabled = mockEnabled,
        mockResponseCode = mockResponseCode,
        mockResponseHeaders = mockResponseHeaders,
        mockResponseBody = mockResponseBody,
        status = status.name
    )
}

/**
 * Convert a summary projection to its DTO
 */
fun TransactionSummary.toDto(): TransactionSummaryDto {
    return TransactionSummaryDto(
        id = id,
        requestDate = requestDate,
        method = method,
        url = url,
        host = host,
        path = path,
        scheme = scheme,
        protocol = protocol,
        requestContentType = requestContentType,
        requestContentLength = requestContentLength,
        requestBodyIsPlainText = requestBodyIsPlainText,
        responseDate = responseDate,
        responseCode = responseCode,
        responseMessage = responseMessage,
        responseContentType = responseContentType,
        responseContentLength = responseContentLength,
        responseBodyIsPlainText = responseBodyIsPlainText,
        duration = duration,
        error = error,
        requestPayloadSize = requestPayloadSize,
        responsePayloadSize = responsePayloadSize,
        isGzipEncoded = isGzipEncoded,
        isMocked = isMocked,
        mockEnabled = mockEnabled,
        mockResponseCode = mockResponseCode,
        status = status.name
    )
}
