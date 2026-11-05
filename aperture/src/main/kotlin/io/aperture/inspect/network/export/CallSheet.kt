package io.aperture.inspect.network.export

import io.aperture.data.entity.HttpTransaction
import io.aperture.inspect.network.export.XlsxWriter.Cell
import io.aperture.inspect.network.export.XlsxWriter.Column
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * The call sequence as one sheet: one row per call, oldest first, with the request and the
 * response side by side. The columns follow the console's own detail view.
 */
internal object CallSheet {

    const val SHEET_NAME = "Calls"

    fun columns(timeZone: TimeZone = TimeZone.getDefault(), now: Long = System.currentTimeMillis()) = listOf(
        Column("#", 6.0),
        Column("Started (${utcOffset(timeZone, now)})", 24.0),
        Column("Duration (ms)", 13.0),
        Column("Method", 9.0),
        Column("Status", 8.0),
        Column("Status text", 16.0),
        Column("URL", 60.0),
        Column("Host", 24.0),
        Column("Path", 40.0),
        Column("Request headers", 48.0),
        Column("Request body", 60.0),
        Column("Response headers", 48.0),
        Column("Response body", 60.0),
        Column("Request size (B)", 15.0),
        Column("Response size (B)", 16.0),
        Column("Mocked", 9.0),
        Column("Error", 40.0),
        Column("cURL", 60.0)
    )

    /** One call as cells, in the order of [columns]. [index] is the call's place in the export. */
    fun row(index: Int, tx: HttpTransaction): List<Cell> = listOf(
        Cell.Number(index),
        Cell.Time(tx.requestDate),
        Cell.Number(tx.duration),
        Cell.Text(tx.method),
        Cell.Number(tx.responseCode),
        Cell.Text(tx.responseMessage),
        Cell.Text(tx.url),
        Cell.Text(tx.host),
        Cell.Text(tx.path),
        Cell.Text(headerLines(tx.requestHeaders)),
        Cell.Text(body(tx.requestBody, tx.requestBodyIsPlainText, tx.requestContentType, tx.requestPayloadSize)),
        Cell.Text(headerLines(tx.responseHeaders)),
        Cell.Text(body(tx.responseBody, tx.responseBodyIsPlainText, tx.responseContentType, tx.responsePayloadSize)),
        Cell.Number(tx.requestPayloadSize),
        Cell.Number(tx.responsePayloadSize),
        Cell.Text(if (tx.isMocked) "yes" else "no"),
        Cell.Text(tx.error),
        Cell.Text(curlCommand(tx))
    )

    // ---------- Headers ----------

    /**
     * Stored headers as `Name: value` lines.
     *
     * The capture stores a list of `{name, value}`; an older or mocked record can hold an
     * object instead. Text that is not JSON comes back as it is.
     */
    fun headerEntries(stored: String?): List<Pair<String, String>>? {
        if (stored.isNullOrBlank()) return emptyList()
        val element = try {
            Json.parseToJsonElement(stored)
        } catch (e: Exception) {
            return null
        }
        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                name to ((obj["value"] as? JsonPrimitive)?.contentOrNull ?: "")
            }
            is JsonObject -> element.flatMap { (name, value) ->
                when (value) {
                    is JsonArray -> value.map { name to text(it) }
                    else -> listOf(name to text(value))
                }
            }
            else -> null
        }
    }

    fun headerLines(stored: String?): String {
        val entries = headerEntries(stored) ?: return stored.orEmpty()
        return entries.joinToString("\n") { (name, value) -> "$name: $value" }
    }

    private fun text(element: JsonElement) = (element as? JsonPrimitive)?.contentOrNull ?: element.toString()

    // ---------- Bodies ----------

    /**
     * A body as a person reads it: JSON indented, a binary body as a note in place of base64.
     */
    fun body(text: String?, plainText: Boolean, contentType: String?, size: Long?): String {
        if (text.isNullOrEmpty()) return ""
        if (!plainText) return "[Binary body, ${size ?: "unknown"} bytes. Not in the export.]"
        val trimmed = text.trimStart()
        val looksJson = (contentType?.contains("json", ignoreCase = true) == true) ||
            trimmed.startsWith("{") || trimmed.startsWith("[")
        if (!looksJson) return text
        try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return text // not JSON after all, or cut short by the body limit
        }
        return indentJson(text)
    }

    /**
     * Indent JSON that is known to be valid, without parsing the values.
     *
     * A parse and print again changes what the server sent: 12.50 comes back as 12.5, and a
     * long id can lose digits. A record of a call must keep every value as it was.
     */
    fun indentJson(json: String, indent: String = "  "): String {
        val out = StringBuilder(json.length + json.length / 4)
        var depth = 0
        var inString = false
        var i = 0
        fun newline() {
            out.append('\n')
            repeat(depth) { out.append(indent) }
        }
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < json.length) {
                    out.append(json[i + 1])
                    i += 1
                } else if (c == '"') {
                    inString = false
                }
            } else when (c) {
                '"' -> {
                    inString = true
                    out.append(c)
                }
                '{', '[' -> {
                    // An empty object or list stays on one line.
                    var next = i + 1
                    while (next < json.length && json[next].isWhitespace()) next += 1
                    if (next < json.length && (json[next] == '}' || json[next] == ']')) {
                        out.append(c).append(json[next])
                        i = next
                    } else {
                        out.append(c)
                        depth += 1
                        newline()
                    }
                }
                '}', ']' -> {
                    depth -= 1
                    newline()
                    out.append(c)
                }
                ',' -> {
                    out.append(c)
                    newline()
                }
                ':' -> out.append(": ")
                ' ', '\t', '\n', '\r' -> Unit
                else -> out.append(c)
            }
            i += 1
        }
        return out.toString()
    }

    // ---------- cURL ----------

    /** The same command the console's "Copy as cURL" gives. Keep the two in step. */
    fun curlCommand(tx: HttpTransaction): String {
        val method = tx.method.uppercase()
        val parts = mutableListOf("curl${if (method == "GET") "" else " -X $method"} ${shellQuote(tx.url)}")
        val headers = (headerEntries(tx.requestHeaders) ?: emptyList())
            .filter { (name) -> !name.equals("Content-Length", ignoreCase = true) }
            .toMutableList()
        val hasBody = !tx.requestBody.isNullOrEmpty()
        val hasContentType = headers.any { (name) -> name.equals("Content-Type", ignoreCase = true) }
        // OkHttp keeps the body's content type on the body, not in the header list.
        if (hasBody && !hasContentType && tx.requestContentType != null) {
            headers += "Content-Type" to tx.requestContentType
        }
        for ((name, value) in headers) parts += "-H ${shellQuote("$name: $value")}"
        if (hasBody) {
            parts += if (!tx.requestBodyIsPlainText) "# Binary request body left out"
            else "--data-raw ${shellQuote(tx.requestBody!!)}"
        }
        return parts.joinToString(" \\\n  ")
    }

    private fun shellQuote(text: String) = "'${text.replace("'", "'\\''")}'"

    // ---------- Time ----------

    /** "UTC+01:00", for the column name. */
    fun utcOffset(timeZone: TimeZone, at: Long): String {
        val minutes = timeZone.getOffset(at) / 60_000
        val sign = if (minutes < 0) "-" else "+"
        return "UTC$sign" + String.format(Locale.ROOT, "%02d:%02d", abs(minutes) / 60, abs(minutes) % 60)
    }
}
