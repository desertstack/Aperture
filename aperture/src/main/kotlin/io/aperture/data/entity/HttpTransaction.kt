package io.aperture.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a complete HTTP transaction including request, response, and mock data.
 * Based on Chucker's schema with enhancements for web-based inspection and mocking.
 */
@Entity(
    tableName = "http_transactions",
    indices = [
        Index(value = ["request_date"], name = "idx_request_date"),
        Index(value = ["url"], name = "idx_url"),
        Index(value = ["method"], name = "idx_method"),
        Index(value = ["response_code"], name = "idx_response_code")
    ]
)
data class HttpTransaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    // Request data
    @ColumnInfo(name = "request_date")
    val requestDate: Long,  // Unix timestamp in milliseconds

    @ColumnInfo(name = "method")
    val method: String,  // GET, POST, etc.

    @ColumnInfo(name = "url")
    val url: String,  // Full URL with query params

    @ColumnInfo(name = "host")
    val host: String,  // Extracted hostname

    @ColumnInfo(name = "path")
    val path: String,  // Path portion of URL

    @ColumnInfo(name = "scheme")
    val scheme: String,  // http/https

    @ColumnInfo(name = "protocol")
    val protocol: String? = null,  // HTTP/1.1, HTTP/2, etc.

    @ColumnInfo(name = "request_content_type")
    val requestContentType: String? = null,

    @ColumnInfo(name = "request_content_length")
    val requestContentLength: Long? = null,

    @ColumnInfo(name = "request_headers")
    val requestHeaders: String? = null,  // JSON serialized

    @ColumnInfo(name = "request_body")
    val requestBody: String? = null,  // Text or Base64 for binary

    @ColumnInfo(name = "request_body_is_plain_text")
    val requestBodyIsPlainText: Boolean = true,

    // Response data
    @ColumnInfo(name = "response_date")
    val responseDate: Long? = null,  // Unix timestamp in milliseconds

    @ColumnInfo(name = "response_code")
    val responseCode: Int? = null,

    @ColumnInfo(name = "response_message")
    val responseMessage: String? = null,  // e.g., "OK", "Not Found"

    @ColumnInfo(name = "response_content_type")
    val responseContentType: String? = null,

    @ColumnInfo(name = "response_content_length")
    val responseContentLength: Long? = null,

    @ColumnInfo(name = "response_headers")
    val responseHeaders: String? = null,  // JSON serialized

    @ColumnInfo(name = "response_body")
    val responseBody: String? = null,  // Text or Base64 for binary

    @ColumnInfo(name = "response_body_is_plain_text")
    val responseBodyIsPlainText: Boolean = true,

    // Timing
    @ColumnInfo(name = "duration")
    val duration: Long? = null,  // Milliseconds

    // Error handling
    @ColumnInfo(name = "error")
    val error: String? = null,  // Error message if request failed

    // Encoding
    @ColumnInfo(name = "request_payload_size")
    val requestPayloadSize: Long? = null,  // Actual size of request payload

    @ColumnInfo(name = "response_payload_size")
    val responsePayloadSize: Long? = null,  // Actual size of response payload

    @ColumnInfo(name = "is_gzip_encoded")
    val isGzipEncoded: Boolean = false,

    // Mock support
    @ColumnInfo(name = "is_mocked")
    val isMocked: Boolean = false,  // If this transaction is being mocked

    @ColumnInfo(name = "mock_enabled")
    val mockEnabled: Boolean = false,  // If mock is currently active

    @ColumnInfo(name = "mock_response_code")
    val mockResponseCode: Int? = null,  // Mocked status code

    @ColumnInfo(name = "mock_response_headers")
    val mockResponseHeaders: String? = null,  // Mocked headers (JSON)

    @ColumnInfo(name = "mock_response_body")
    val mockResponseBody: String? = null  // Mocked response body
) {
    /**
     * Transaction status based on current state
     */
    enum class Status {
        REQUESTED,   // Request sent, waiting for response
        COMPLETE,    // Response received successfully
        FAILED       // Request or response failed with error
    }

    /**
     * Get the current status of this transaction
     */
    val status: Status
        get() = when {
            error != null -> Status.FAILED
            responseCode == null -> Status.REQUESTED
            else -> Status.COMPLETE
        }

    /**
     * Check if this is an SSL/TLS connection
     */
    val isSsl: Boolean
        get() = scheme.equals("https", ignoreCase = true)

    /**
     * Get formatted size string for request + response
     */
    fun getTotalSize(): Long = (requestPayloadSize ?: 0) + (responsePayloadSize ?: 0)

    /**
     * Check if response was successful (2xx status code)
     */
    fun isSuccessful(): Boolean = responseCode in 200..299

    /**
     * Check if transaction has completed (has response or error)
     */
    fun isComplete(): Boolean = status != Status.REQUESTED
}
