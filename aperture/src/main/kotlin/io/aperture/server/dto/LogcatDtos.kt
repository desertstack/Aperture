package io.aperture.server.dto

import kotlinx.serialization.Serializable

/**
 * One log line as the console receives it.
 *
 * [level] is one letter, V D I W E F or A, exactly as logcat prints it. The console colours by it.
 */
@Serializable
data class LogLineDto(
    val seq: Long,
    val time: String,
    val pid: Int,
    val tid: Int,
    val level: String,
    val tag: String,
    val message: String
)

/**
 * Lines sent together in one live event.
 *
 * A busy app can log thousands of lines a second. One event per line would cost a write and a
 * flush for each, so the stream sends what has piled up since the last write.
 */
@Serializable
data class LogBatchDto(
    val lines: List<LogLineDto>
)

/** What the Logcat panel shows before the stream opens. */
@Serializable
data class LogcatStatusDto(
    /** True while a `logcat` process is reading for this app. */
    val reading: Boolean,
    /** The process whose lines Aperture shows. */
    val pid: Int,
    /** The newest line number, or 0 before the first. */
    val lastSeq: Long,
    /** How many lines the device keeps for a browser that connects late. */
    val capacity: Int,
    /** Why the reader could not start, when it could not. */
    val error: String?,
    /**
     * Names this run of the reader. Line numbers start again at 1 when the app restarts, so the
     * console keeps this beside the last number it saw.
     */
    val session: String
)
