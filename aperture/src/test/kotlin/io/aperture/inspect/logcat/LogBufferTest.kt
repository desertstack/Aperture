package io.aperture.inspect.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line number is how a browser that reconnects asks for only what it missed, and how it
 * notices a gap. It must only go up, and the buffer must answer "after N" exactly.
 */
class LogBufferTest {

    private fun entry(message: String) = LogEntry("09-28 14:03:11.482", 1, 1, 'I', "T", message)

    @Test
    fun `numbers start at one and only go up`() {
        val buffer = LogBuffer(10)

        val seqs = (1..3).map { buffer.append(entry("m$it")).seq }

        assertEquals(listOf(1L, 2L, 3L), seqs)
        assertEquals(3L, buffer.lastSeq())
    }

    @Test
    fun `since gives only the lines after the number`() {
        val buffer = LogBuffer(10)
        repeat(5) { buffer.append(entry("m${it + 1}")) }

        assertEquals(listOf("m4", "m5"), buffer.since(3).map { it.message })
        assertEquals(5, buffer.since(0).size)
        assertTrue(buffer.since(5).isEmpty())
        assertTrue(buffer.since(99).isEmpty())
    }

    @Test
    fun `a full buffer drops the oldest and keeps counting`() {
        val buffer = LogBuffer(3)
        repeat(5) { buffer.append(entry("m${it + 1}")) }

        assertEquals(listOf(3L, 4L, 5L), buffer.since(0).map { it.seq })
        // Asking after a line that has been dropped gives everything still kept.
        assertEquals(listOf(3L, 4L, 5L), buffer.since(1).map { it.seq })
        assertEquals(listOf(5L), buffer.since(4).map { it.seq })
    }

    @Test
    fun `an empty buffer has nothing to give`() {
        val buffer = LogBuffer(3)

        assertTrue(buffer.since(0).isEmpty())
        assertEquals(0L, buffer.lastSeq())
        assertEquals(null, buffer.lastTime())
    }
}
