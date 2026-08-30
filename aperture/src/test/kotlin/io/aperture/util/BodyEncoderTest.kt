package io.aperture.util

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android reads a row through a CursorWindow of about 2 MB. A row above that limit cannot be
 * read back at all: every query that touches it throws SQLiteBlobTooBigException. Whatever the
 * caller configures, a stored body has to stay under the ceiling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BodyEncoderTest {

    private val fiveMegabytes = 5L * 1024 * 1024

    @Test
    fun `caps a plain text body at the ceiling`() {
        val buffer = Buffer().writeUtf8("a".repeat(5 * 1024 * 1024))

        val (body, isPlainText) = BodyEncoder.encodeBody(buffer, "text/plain", fiveMegabytes)

        assertTrue(isPlainText)
        assertTrue("stored ${body.length} chars", body.length <= BodyEncoder.MAX_STORED_BODY_CHARS)
    }

    @Test
    fun `caps a base64 body at the ceiling`() {
        // Base64 grows by a third, so capping the byte count alone is not enough.
        val binary = ByteArray(3 * 1024 * 1024) { (it % 256).toByte() }
        val buffer = Buffer().write(binary)

        val (body, isPlainText) = BodyEncoder.encodeBody(
            buffer,
            "application/octet-stream",
            fiveMegabytes
        )

        assertFalse(isPlainText)
        assertTrue("stored ${body.length} chars", body.length <= BodyEncoder.MAX_STORED_BODY_CHARS)
    }

    @Test
    fun `says so when it truncates`() {
        val buffer = Buffer().writeUtf8("a".repeat(2 * 1024 * 1024))

        val (body, _) = BodyEncoder.encodeBody(buffer, "text/plain", fiveMegabytes)

        assertTrue("no notice in: ...${body.takeLast(80)}", body.contains("truncated"))
    }

    @Test
    fun `leaves a small body alone`() {
        val buffer = Buffer().writeUtf8("""{"ok":true}""")

        val (body, isPlainText) = BodyEncoder.encodeBody(buffer, "application/json", fiveMegabytes)

        assertTrue(isPlainText)
        assertEquals("""{"ok":true}""", body)
    }
}
