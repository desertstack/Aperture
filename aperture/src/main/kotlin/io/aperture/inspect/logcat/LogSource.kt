package io.aperture.inspect.logcat

import android.os.Build
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader

/**
 * Where log lines come from.
 *
 * On a device that is a `logcat` process. In a test it is a list of strings, so the reader loop
 * and the stream can be exercised without a device.
 */
internal fun interface LogSource {
    /**
     * Start reading.
     *
     * @param pid the process to read for
     * @param resumeAfter null on the first open, for recent history. After a reader stopped
     *   early, the newest time already read, so the new reader continues from there.
     */
    fun open(pid: Int, resumeAfter: String?): OpenLog
}

/** A reader in progress. Closing it must make a blocked [BufferedReader.readLine] return. */
internal class OpenLog(
    val reader: BufferedReader,
    private val onClose: () -> Unit
) : Closeable {
    override fun close() {
        runCatching { onClose() }
        runCatching { reader.close() }
    }
}

/**
 * `logcat` itself.
 *
 * An app may read its own log lines without any permission. Other apps' lines are not visible
 * to it on any release Aperture supports, and `--pid` narrows further to this one process, so
 * a second process of the same app does not interleave with this one.
 */
internal class ProcessLogSource(
    private val history: Int = LogBuffer.DEFAULT_CAPACITY
) : LogSource {

    override fun open(pid: Int, resumeAfter: String?): OpenLog {
        val args = mutableListOf("logcat", "-v", "threadtime")
        // --pid arrived in API 24. Below that, the reader drops other processes' lines itself.
        if (Build.VERSION.SDK_INT >= 24) args += "--pid=$pid"
        // -T prints recent lines and then keeps following, where -t would print and exit.
        args += "-T"
        args += resumeAfter ?: history.toString()

        val process = ProcessBuilder(args)
            // A bad argument is reported on stderr. Merged, it reaches the reader, which keeps
            // the last line it could not parse as the reason the process ended.
            .redirectErrorStream(true)
            .start()
        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        return OpenLog(reader) { process.destroy() }
    }
}
