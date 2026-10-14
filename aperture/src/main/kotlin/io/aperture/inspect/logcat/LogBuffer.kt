package io.aperture.inspect.logcat

import io.aperture.server.dto.LogLineDto

/**
 * The most recent log lines, each with a sequence number that only goes up.
 *
 * The number is what lets a browser that reconnects ask for "everything after 4120" instead of
 * getting the whole buffer again, and what lets it see a gap when lines were dropped on the way.
 */
internal class LogBuffer(val capacity: Int = DEFAULT_CAPACITY) {

    private val lines = ArrayDeque<LogLineDto>(capacity)
    private var nextSeq = 1L

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    /** Give [entry] the next number, keep it, and drop the oldest line when full. */
    @Synchronized
    fun append(entry: LogEntry): LogLineDto {
        val line = LogLineDto(
            seq = nextSeq++,
            time = entry.time,
            pid = entry.pid,
            tid = entry.tid,
            level = entry.level.toString(),
            tag = entry.tag,
            message = entry.message
        )
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
        return line
    }

    /** Every kept line numbered above [after], oldest first. */
    @Synchronized
    fun since(after: Long): List<LogLineDto> {
        if (lines.isEmpty() || lines.last().seq <= after) return emptyList()
        // Numbers are consecutive inside the buffer, so the start is arithmetic, not a search.
        val skip = (after - lines.first().seq + 1).coerceIn(0L, lines.size.toLong()).toInt()
        return lines.subList(skip, lines.size).toList()
    }

    /** The number of the newest line, or 0 before the first. */
    @Synchronized
    fun lastSeq(): Long = nextSeq - 1

    /** The newest time logcat printed, for resuming a reader after it stopped. */
    @Synchronized
    fun lastTime(): String? = lines.lastOrNull()?.time

    companion object {
        const val DEFAULT_CAPACITY = 5_000
    }
}
