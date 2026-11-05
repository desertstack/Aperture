package io.aperture.inspect.network.export

import io.aperture.data.entity.HttpTransaction
import io.aperture.inspect.network.export.XlsxWriter.Cell
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class CallSheetTest {

    private val post = HttpTransaction(
        id = 7,
        requestDate = 1_700_000_000_123L,
        method = "POST",
        url = "https://api.example.com/v2/cart/items",
        host = "api.example.com",
        path = "/v2/cart/items",
        scheme = "https",
        requestContentType = "application/json; charset=utf-8",
        requestHeaders = """[{"name":"Accept","value":"application/json"},{"name":"Content-Length","value":"25"}]""",
        requestBody = """{"sku":"CBL-004","qty":3}""",
        responseCode = 201,
        responseMessage = "Created",
        responseContentType = "application/json",
        responseHeaders = """[{"name":"Server","value":"nginx"}]""",
        responseBody = """{"id":"line_88","price":12.50}""",
        duration = 341,
        requestPayloadSize = 25,
        responsePayloadSize = 31
    )

    @Test
    fun `a row has one cell per column`() {
        assertEquals(CallSheet.columns().size, CallSheet.row(1, post).size)
    }

    @Test
    fun `headers are one name and value per line`() {
        assertEquals("Accept: application/json\nContent-Length: 25", CallSheet.headerLines(post.requestHeaders))
        assertEquals("X-A: 1\nX-B: 2\nX-B: 3", CallSheet.headerLines("""{"X-A":"1","X-B":["2","3"]}"""))
        assertEquals("not json", CallSheet.headerLines("not json"))
    }

    @Test
    fun `a JSON body is indented and keeps its numbers as written`() {
        val row = CallSheet.row(1, post)

        assertEquals(Cell.Text("{\n  \"id\": \"line_88\",\n  \"price\": 12.50\n}"), row[12])
    }

    @Test
    fun `indenting keeps long numbers, escapes and empty objects as they are`() {
        assertEquals(
            "{\n  \"id\": 12345678901234567890,\n  \"s\": \"a, b: {c}\\\" d\",\n  \"e\": {},\n  \"l\": [\n    1,\n    2\n  ]\n}",
            CallSheet.indentJson("""{ "id":12345678901234567890,"s":"a, b: {c}\" d","e":{ },"l":[1,2]}""")
        )
    }

    @Test
    fun `a body that is not JSON stays as it is`() {
        assertEquals("<p>500</p>", CallSheet.body("<p>500</p>", true, "text/html", 10))
        assertEquals("{cut", CallSheet.body("{cut", true, "application/json", 4))
    }

    @Test
    fun `a binary body is a note, not base64`() {
        assertEquals(
            "[Binary body, 2048 bytes. Not in the export.]",
            CallSheet.body("iVBORw0KGgo=", false, "image/png", 2048)
        )
    }

    @Test
    fun `the cURL matches the console's`() {
        assertEquals(
            "curl -X POST 'https://api.example.com/v2/cart/items' \\\n" +
                "  -H 'Accept: application/json' \\\n" +
                "  -H 'Content-Type: application/json; charset=utf-8' \\\n" +
                "  --data-raw '{\"sku\":\"CBL-004\",\"qty\":3}'",
            CallSheet.curlCommand(post)
        )
    }

    @Test
    fun `a quote in the body is escaped for the shell`() {
        val tx = post.copy(requestBody = "it's")
        assertEquals(true, CallSheet.curlCommand(tx).endsWith("--data-raw 'it'\\''s'"))
    }

    @Test
    fun `the offset in the column name follows the zone`() {
        assertEquals("UTC+05:30", CallSheet.utcOffset(TimeZone.getTimeZone("Asia/Kolkata"), 0L))
        assertEquals("UTC-03:00", CallSheet.utcOffset(TimeZone.getTimeZone("America/Sao_Paulo"), 1_700_000_000_000L))
        assertEquals("UTC+00:00", CallSheet.utcOffset(TimeZone.getTimeZone("UTC"), 0L))
    }
}
