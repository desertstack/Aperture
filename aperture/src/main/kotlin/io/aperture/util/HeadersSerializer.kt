package io.aperture.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers

/**
 * Utility for serializing HTTP headers to/from JSON
 */
object HeadersSerializer {

    private val json = Json { prettyPrint = false }

    @Serializable
    data class Header(val name: String, val value: String)

    /**
     * Serialize OkHttp Headers to JSON string
     */
    fun serialize(headers: Headers, headersToRedact: Set<String> = emptySet()): String {
        val headerList = mutableListOf<Header>()

        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = if (headersToRedact.any { it.equals(name, ignoreCase = true) }) {
                "[REDACTED]"
            } else {
                headers.value(i)
            }
            headerList.add(Header(name, value))
        }

        return json.encodeToString(headerList)
    }

    /**
     * Deserialize JSON string to Headers
     */
    fun deserialize(jsonString: String): Headers {
        val headerList = json.decodeFromString<List<Header>>(jsonString)
        val builder = Headers.Builder()

        for (header in headerList) {
            builder.add(header.name, header.value)
        }

        return builder.build()
    }

    /**
     * Serialize a map of headers to JSON
     */
    fun serializeMap(headers: Map<String, String>): String {
        val headerList = headers.map { (name, value) -> Header(name, value) }
        return json.encodeToString(headerList)
    }
}
