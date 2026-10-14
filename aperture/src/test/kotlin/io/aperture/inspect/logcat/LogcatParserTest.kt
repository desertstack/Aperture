package io.aperture.inspect.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The console colours a line by its level and groups a stack trace by its header, so both have
 * to come out of the text exactly. A line that does not parse is lost, not shown garbled.
 */
class LogcatParserTest {

    @Test
    fun `a threadtime line splits into its fields`() {
        val entry = LogcatParser.parse("09-28 14:03:11.482  4321  4390 W OkHttp  : Connection leaked")!!

        assertEquals("09-28 14:03:11.482", entry.time)
        assertEquals(4321, entry.pid)
        assertEquals(4390, entry.tid)
        assertEquals('W', entry.level)
        assertEquals("OkHttp", entry.tag)
        assertEquals("Connection leaked", entry.message)
    }

    @Test
    fun `a colon in the message stays in the message`() {
        val entry = LogcatParser.parse("09-28 14:03:11.482  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main")!!

        assertEquals("AndroidRuntime", entry.tag)
        assertEquals("FATAL EXCEPTION: main", entry.message)
    }

    @Test
    fun `a tag with dots and a long name is kept whole`() {
        val entry = LogcatParser.parse("01-02 03:04:05.006 12345 12399 I com.example.sync.Worker: done")!!

        assertEquals("com.example.sync.Worker", entry.tag)
        assertEquals("done", entry.message)
    }

    @Test
    fun `an empty message is still a line`() {
        val entry = LogcatParser.parse("09-28 14:03:11.482  4321  4390 D Tag     : ")!!

        assertEquals("Tag", entry.tag)
        assertEquals("", entry.message)
    }

    @Test
    fun `every level letter is read`() {
        for (level in "VDIWEFA") {
            val entry = LogcatParser.parse("09-28 14:03:11.482  1  1 $level T: m")
            assertEquals(level, entry?.level)
        }
    }

    @Test
    fun `buffer banners and noise carry no message`() {
        assertNull(LogcatParser.parse("--------- beginning of main"))
        assertNull(LogcatParser.parse("--------- beginning of crash"))
        assertNull(LogcatParser.parse(""))
        assertNull(LogcatParser.parse("logcat: Unknown option"))
    }

    @Test
    fun `an oversized message is cut`() {
        val long = "x".repeat(LogcatParser.MAX_MESSAGE + 100)
        val entry = LogcatParser.parse("09-28 14:03:11.482  1  1 I T: $long")!!

        assertTrue(entry.message.length <= LogcatParser.MAX_MESSAGE + 1)
    }

    @Test
    fun `levels rank from verbose to assert`() {
        val ranks = "VDIWEA".map { LogcatParser.rank(it) }
        assertEquals(ranks.sorted(), ranks)
        assertEquals(LogcatParser.rank('F'), LogcatParser.rank('A'))
    }
}
