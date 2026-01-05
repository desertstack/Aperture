package io.aperture.util

import android.util.Base64
import okio.Buffer
import java.io.EOFException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Utility for encoding and decoding request/response bodies
 */
object BodyEncoder {

    /**
     * Check if content type is plain text
     */
    fun isPlainText(contentType: String?): Boolean {
        if (contentType == null) return true

        val lowerType = contentType.lowercase()
        return lowerType.contains("text/") ||
                lowerType.contains("application/json") ||
                lowerType.contains("application/xml") ||
                lowerType.contains("application/x-www-form-urlencoded") ||
                lowerType.contains("application/javascript") ||
                lowerType.contains("application/xhtml") ||
                lowerType.contains("+xml") ||
                lowerType.contains("+json")
    }

    /**
     * Check if buffer is likely plain text
     */
    fun isPlainText(buffer: Buffer): Boolean {
        try {
            val prefix = Buffer()
            val byteCount = minOf(buffer.size, 64)
            buffer.copyTo(prefix, 0, byteCount)

            for (i in 0 until 16) {
                if (prefix.exhausted()) break
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            return true
        } catch (e: EOFException) {
            return false
        }
    }

    /**
     * Encode body content (as text or base64)
     */
    fun encodeBody(
        buffer: Buffer,
        contentType: String?,
        maxSize: Long
    ): Pair<String, Boolean> {
        val size = buffer.size

        // If body exceeds max size, truncate
        val actualSize = minOf(size, maxSize)

        // Check if plain text
        val isPlainText = isPlainText(contentType) && isPlainText(buffer)

        val body = if (isPlainText) {
            // Store as text
            val charset = getCharset(contentType)
            val bytes = buffer.readByteArray(actualSize)
            String(bytes, charset)
        } else {
            // Store as base64
            val bytes = buffer.readByteArray(actualSize)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        return Pair(body, isPlainText)
    }

    /**
     * Decode body content
     */
    fun decodeBody(body: String, isPlainText: Boolean): ByteArray {
        return if (isPlainText) {
            body.toByteArray(StandardCharsets.UTF_8)
        } else {
            Base64.decode(body, Base64.NO_WRAP)
        }
    }

    /**
     * Get charset from content type
     */
    private fun getCharset(contentType: String?): Charset {
        if (contentType == null) return StandardCharsets.UTF_8

        try {
            val parts = contentType.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("charset=", ignoreCase = true)) {
                    val charsetName = trimmed.substring(8).trim('"', '\'')
                    return Charset.forName(charsetName)
                }
            }
        } catch (e: Exception) {
            // Fallback to UTF-8
        }

        return StandardCharsets.UTF_8
    }
}
