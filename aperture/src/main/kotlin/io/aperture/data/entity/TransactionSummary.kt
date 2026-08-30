package io.aperture.data.entity

import androidx.room.ColumnInfo

/**
 * A transaction without its bodies and headers
 *
 * The list view needs metadata only. Selecting the bodies as well pulls every stored body of
 * every listed row into memory, which a 512 KB ceiling and a limit of 500 rows turn into
 * hundreds of megabytes inside the host app. Room fills this from a column projection, so the
 * bodies never leave SQLite.
 */
data class TransactionSummary(
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "request_date")
    val requestDate: Long,

    @ColumnInfo(name = "method")
    val method: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "host")
    val host: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "scheme")
    val scheme: String,

    @ColumnInfo(name = "protocol")
    val protocol: String? = null,

    @ColumnInfo(name = "request_content_type")
    val requestContentType: String? = null,

    @ColumnInfo(name = "request_content_length")
    val requestContentLength: Long? = null,

    @ColumnInfo(name = "request_body_is_plain_text")
    val requestBodyIsPlainText: Boolean = true,

    @ColumnInfo(name = "response_date")
    val responseDate: Long? = null,

    @ColumnInfo(name = "response_code")
    val responseCode: Int? = null,

    @ColumnInfo(name = "response_message")
    val responseMessage: String? = null,

    @ColumnInfo(name = "response_content_type")
    val responseContentType: String? = null,

    @ColumnInfo(name = "response_content_length")
    val responseContentLength: Long? = null,

    @ColumnInfo(name = "response_body_is_plain_text")
    val responseBodyIsPlainText: Boolean = true,

    @ColumnInfo(name = "duration")
    val duration: Long? = null,

    @ColumnInfo(name = "error")
    val error: String? = null,

    @ColumnInfo(name = "request_payload_size")
    val requestPayloadSize: Long? = null,

    @ColumnInfo(name = "response_payload_size")
    val responsePayloadSize: Long? = null,

    @ColumnInfo(name = "is_gzip_encoded")
    val isGzipEncoded: Boolean = false,

    @ColumnInfo(name = "is_mocked")
    val isMocked: Boolean = false,

    @ColumnInfo(name = "mock_enabled")
    val mockEnabled: Boolean = false,

    @ColumnInfo(name = "mock_response_code")
    val mockResponseCode: Int? = null
) {
    /**
     * Same rule as HttpTransaction.status, from the same columns
     */
    val status: HttpTransaction.Status
        get() = when {
            error != null -> HttpTransaction.Status.FAILED
            responseCode == null -> HttpTransaction.Status.REQUESTED
            else -> HttpTransaction.Status.COMPLETE
        }
}
