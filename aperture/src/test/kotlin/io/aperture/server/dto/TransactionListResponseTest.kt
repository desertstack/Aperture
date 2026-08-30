package io.aperture.server.dto

import io.aperture.data.entity.TransactionSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GET /api/transactions answers with metadata only. A client that wants a body asks for the one
 * transaction it is showing. Putting bodies back in the list would hold up to 500 of them in
 * memory inside the host app and serialize the lot to JSON.
 */
class TransactionListResponseTest {

    private val summary = TransactionSummary(
        id = 7,
        requestDate = 1_700_000_000_000L,
        method = "POST",
        url = "http://example.com/upload",
        host = "example.com",
        path = "/upload",
        scheme = "http",
        responseCode = 200,
        duration = 42
    )

    private fun listJson() = Json.encodeToString(
        TransactionListResponse.serializer(),
        TransactionListResponse(
            transactions = listOf(summary.toDto()),
            total = 1,
            limit = 50,
            offset = 0
        )
    )

    @Test
    fun `carries no bodies or headers`() {
        val json = listJson()

        for (field in listOf(
            "requestBody",
            "responseBody",
            "requestHeaders",
            "responseHeaders",
            "mockResponseBody",
            "mockResponseHeaders"
        )) {
            assertFalse("list response leaks $field", json.contains("\"$field\""))
        }
    }

    @Test
    fun `carries what the list view draws`() {
        val json = listJson()

        for (field in listOf(
            "id",
            "method",
            "url",
            "responseCode",
            "duration",
            "requestDate",
            "requestPayloadSize",
            "responsePayloadSize",
            "isMocked",
            "error"
        )) {
            assertTrue("list response is missing $field", json.contains("\"$field\""))
        }
    }

    @Test
    fun `reports the same status as the full transaction`() {
        assertEquals("COMPLETE", summary.toDto().status)
        assertEquals("FAILED", summary.copy(error = "timeout").toDto().status)
        assertEquals("REQUESTED", summary.copy(responseCode = null).toDto().status)
    }
}
