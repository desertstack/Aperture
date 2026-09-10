package io.aperture.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The console has always offered a status filter and it has never worked: the server read the
 * class it was sent with toIntOrNull(), which is null for "4xx", so every request came back
 * unfiltered. Someone hunting a failing call saw the whole list instead.
 */
class StatusFilterTest {

    @Test
    fun `a class covers its whole hundred`() {
        assertEquals(200..299, StatusFilter.parse("2xx"))
        assertEquals(400..499, StatusFilter.parse("4xx"))
        assertEquals(500..599, StatusFilter.parse("5xx"))
    }

    @Test
    fun `an exact code matches only itself`() {
        assertEquals(404..404, StatusFilter.parse("404"))
    }

    @Test
    fun `case and spacing do not matter`() {
        assertEquals(400..499, StatusFilter.parse(" 4XX "))
    }

    @Test
    fun `nothing to filter on reads as no filter`() {
        assertNull(StatusFilter.parse(null))
        assertNull(StatusFilter.parse(""))
        assertNull(StatusFilter.parse("   "))
        assertNull(StatusFilter.parse("all"))
    }

    @Test
    fun `a value that is not a status is refused, not guessed`() {
        assertNull(StatusFilter.parse("xxx"))
        assertNull(StatusFilter.parse("9xx"))
        assertNull(StatusFilter.parse("0xx"))
        assertNull(StatusFilter.parse("99"))
        assertNull(StatusFilter.parse("600"))
        assertNull(StatusFilter.parse("2xx; DROP TABLE"))
    }
}
