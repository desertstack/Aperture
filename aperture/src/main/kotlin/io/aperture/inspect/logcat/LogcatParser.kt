package io.aperture.inspect.logcat

/**
 * One line of `logcat -v threadtime`, before it has a sequence number.
 *
 * [time] stays as logcat printed it, `MM-DD HH:MM:SS.mmm`. The format has no year and no zone,
 * so turning it into an instant here would only invent both.
 */
internal data class LogEntry(
    val time: String,
    val pid: Int,
    val tid: Int,
    val level: Char,
    val tag: String,
    val message: String
)

/**
 * Reads the `threadtime` format, which every Android release from API 21 up prints the same way:
 *
 * ```
 * 09-28 14:03:11.482  4321  4390 W OkHttp  : Connection leaked
 * ```
 *
 * A message with a newline in it arrives as several lines that repeat the header, so each line
 * parses on its own. The console groups them again for display.
 */
internal object LogcatParser {

    /** Bytes above this are cut. logd itself stops near 4 KB, so this only guards a bad reader. */
    const val MAX_MESSAGE = 8 * 1024

    private val THREADTIME = Regex(
        """^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s(.*?)\s*: ?(.*)$"""
    )

    /**
     * @return the entry, or null for a line that carries no log message, such as the
     *   `--------- beginning of main` banner logcat prints when it opens a buffer.
     */
    fun parse(line: String): LogEntry? {
        if (line.isEmpty() || line.startsWith("---------")) return null
        val match = THREADTIME.matchEntire(line) ?: return null
        val (time, pid, tid, level, tag, message) = match.destructured
        return LogEntry(
            time = time,
            pid = pid.toIntOrNull() ?: return null,
            tid = tid.toIntOrNull() ?: return null,
            level = level[0],
            tag = tag,
            message = if (message.length > MAX_MESSAGE) message.take(MAX_MESSAGE) + "…" else message
        )
    }

    /** Where 'V' is 0 and 'A' is highest. An unknown letter counts as verbose. */
    fun rank(level: Char): Int = when (level) {
        'V' -> 0
        'D' -> 1
        'I' -> 2
        'W' -> 3
        'E' -> 4
        'F', 'A' -> 5
        else -> 0
    }
}
