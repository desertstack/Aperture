package io.aperture.util

/**
 * Reads the status filter the console sends.
 *
 * The console has always sent a class such as `4xx`, and the server has always read it with
 * `toIntOrNull()`, which returns null. The filter therefore did nothing at all. This turns both
 * a class and an exact code into a range the query can use.
 */
object StatusFilter {

    /**
     * @param value `2xx`, `3xx`, `4xx`, `5xx`, or an exact code such as `404`.
     * @return the codes to keep, or null when [value] means "no filter".
     */
    fun parse(value: String?): IntRange? {
        val text = value?.trim()?.lowercase() ?: return null
        if (text.isEmpty() || text == "all") return null

        if (text.length == 3 && text.endsWith("xx")) {
            val hundreds = text[0].digitToIntOrNull() ?: return null
            if (hundreds !in 1..5) return null
            return (hundreds * 100)..(hundreds * 100 + 99)
        }

        val exact = text.toIntOrNull() ?: return null
        if (exact !in 100..599) return null
        return exact..exact
    }
}
