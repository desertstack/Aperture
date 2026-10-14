package io.aperture.inspect.logcat

import android.os.Process
import android.util.Log
import io.aperture.inspect.InspectorModule
import io.aperture.server.ApertureBus
import io.aperture.server.dto.LogBatchDto
import io.aperture.server.dto.LogLineDto
import io.aperture.server.dto.LogcatStatusDto
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Writer
import java.util.UUID
import kotlin.concurrent.thread

/**
 * This app's own log, live.
 *
 * Nothing runs until a browser opens the Logcat panel. The first viewer starts one `logcat`
 * process; every later viewer shares it. It keeps reading until the server stops, so a panel
 * that closes and opens again picks up where it left off instead of starting a new process.
 *
 * The lines travel on their own stream, `/api/logcat/stream`, not on the shared event bus. That
 * bus drops the oldest events when a browser falls behind, and an app that logs in a tight loop
 * would push every network and storage event out of it.
 */
internal class LogcatInspector(
    private val source: LogSource = ProcessLogSource(),
    private val pid: Int = Process.myPid(),
    capacity: Int = LogBuffer.DEFAULT_CAPACITY
) : InspectorModule {

    override val id = "logcat"
    override val label = "Logcat"
    override val icon = "terminal"
    override val writable = false

    private val tag = "ApertureLogcat"

    private val buffer = LogBuffer(capacity)

    /**
     * Names this run of the reader. Line numbers start again at 1 when the app restarts, so a
     * browser that remembers "after 4120" also has to remember whose 4120 it was.
     */
    private val session = UUID.randomUUID().toString().take(8)

    /** Lines and status changes, in the order they happened. */
    private val live = MutableSharedFlow<LiveItem>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val lock = Any()
    private var reader: Thread? = null

    /** The reader run that is current. A reader whose run is no longer current stops. */
    @Volatile
    private var run: Any? = null

    @Volatile
    private var current: OpenLog? = null

    @Volatile
    private var lastError: String? = null

    override fun isAvailable(): Boolean = true

    override fun register(route: Route) {
        route.get { call.respond(status()) }

        route.get("/stream") {
            ensureReading()

            // A browser that reconnects on its own sends the last id it saw as a header. A
            // panel that opens again sends it as a parameter. Either way, only lines after it.
            val after = resumePoint(
                call.request.header("Last-Event-ID") ?: call.request.queryParameters["since"]
            )

            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.header("X-Accel-Buffering", "no")

            try {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    stream(this, after)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The browser went. Nothing is logged: this panel would show the line itself,
                // once for every tab that closed.
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            run = null
            current?.close()
            current = null
            reader = null
        }
    }

    // ---------- The stream ----------

    /**
     * Write the live stream to [out] until the browser goes, which shows as a failed write, or
     * until the caller is cancelled.
     */
    internal suspend fun stream(out: Writer, after: Long): Unit = coroutineScope {
        val queue = live
            // Runs once the subscription is in place, so no line falls between the history and
            // the live feed. A line can arrive in both; the sequence check below drops the copy.
            .onSubscription { for (line in buffer.since(after)) emit(LiveItem.Line(line)) }
            // A browser that cannot keep up loses the oldest lines, not the app's time. The
            // console sees the gap in the numbers and says how many it missed.
            .buffer(STREAM_QUEUE, BufferOverflow.DROP_OLDEST)
            .produceIn(this)

        out.event("logcat_status", ApertureBus.json.encodeToString(LogcatStatusDto.serializer(), status()))

        var sent = after
        val batch = ArrayList<LogLineDto>(BATCH)
        while (true) {
            val first = withTimeoutOrNull(HEARTBEAT_MS) { queue.receive() }
            if (first == null) {
                // A comment line. It keeps proxies from closing a quiet stream and finds out
                // that a browser has gone, which only a failed write can tell.
                out.write(": ping\n\n")
                out.flush()
                continue
            }

            batch.clear()
            var item: LiveItem? = first
            while (item != null) {
                when (item) {
                    is LiveItem.Line -> if (item.line.seq > sent) batch += item.line
                    is LiveItem.Status -> {
                        sent = out.flushBatch(batch, sent)
                        out.event("logcat_status", ApertureBus.json.encodeToString(LogcatStatusDto.serializer(), item.status))
                    }
                }
                item = if (batch.size < BATCH) queue.tryReceive().getOrNull() else null
            }
            sent = out.flushBatch(batch, sent)
        }
    }

    /** Send [batch] as one event, if it has anything. Returns the newest number sent. */
    private fun Writer.flushBatch(batch: MutableList<LogLineDto>, sent: Long): Long {
        if (batch.isEmpty()) return sent
        val last = batch.last().seq
        event(
            type = "logcat_lines",
            json = ApertureBus.json.encodeToString(LogBatchDto.serializer(), LogBatchDto(batch.toList())),
            id = "$session:$last"
        )
        batch.clear()
        return last
    }

    private fun Writer.event(type: String, json: String, id: String? = null) {
        if (id != null) write("id: $id\n")
        write("event: $type\n")
        write("data: $json\n\n")
        flush()
    }

    /** `session:seq` from this run gives seq. Anything else, including another run's, gives 0. */
    internal fun resumePoint(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val mark = value.substringBefore(':', "")
        if (mark != session) return 0L
        return value.substringAfter(':').toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    // ---------- The reader ----------

    internal fun status() = LogcatStatusDto(
        reading = run != null && reader?.isAlive == true,
        pid = pid,
        lastSeq = buffer.lastSeq(),
        capacity = buffer.capacity,
        error = lastError,
        session = session
    )

    /** Start the reader, unless one is already running. */
    internal fun ensureReading() {
        synchronized(lock) {
            if (run != null && reader?.isAlive == true) return
            val mine = Any()
            run = mine
            lastError = null
            reader = thread(name = "Aperture-logcat", isDaemon = true) { readLoop(mine) }
        }
    }

    private fun readLoop(mine: Any) {
        var failures = 0
        while (run === mine) {
            val resumeAfter = buffer.lastTime()
            val produced = readOnce(mine, resumeAfter)
            if (run !== mine) break

            // logcat ends when the log daemon restarts or the process is killed. Start another,
            // but give up on one that ends at once every time, such as a device that refuses it.
            failures = if (produced > 0) 0 else failures + 1
            if (failures >= MAX_FAILURES) {
                lastError = lastError ?: "logcat stopped and did not start again."
                Log.w(tag, "Giving up on logcat: $lastError")
                announce()
                synchronized(lock) { if (run === mine) run = null }
                break
            }
            Thread.sleep(RETRY_BASE_MS shl (failures.coerceAtMost(4)))
        }
    }

    /** Read from one logcat process until it ends. Returns how many lines it gave. */
    private fun readOnce(mine: Any, resumeAfter: String?): Int {
        val open = try {
            source.open(pid, resumeAfter)
        } catch (e: Exception) {
            lastError = "Could not start logcat: ${e.message}"
            Log.w(tag, lastError, e)
            announce()
            return 0
        }

        synchronized(lock) {
            if (run !== mine) {
                open.close()
                return 0
            }
            current = open
        }

        var produced = 0
        var unparsed: String? = null
        // After a restart, logcat prints from the last time already seen, that line included.
        var catchingUp = resumeAfter != null
        try {
            while (run === mine) {
                val raw = open.reader.readLine() ?: break
                val entry = LogcatParser.parse(raw)
                if (entry == null) {
                    if (raw.isNotBlank() && !raw.startsWith("---------")) unparsed = raw
                    continue
                }
                // Below API 24 logcat cannot filter by process, so the reader does.
                if (entry.pid != pid) continue
                if (catchingUp) {
                    if (entry.time <= resumeAfter!!) continue
                    catchingUp = false
                }
                val line = buffer.append(entry)
                live.tryEmit(LiveItem.Line(line))
                if (produced == 0 && lastError != null) {
                    lastError = null
                    announce()
                }
                produced++
            }
        } catch (e: Exception) {
            // Closing the process from stop() lands here. Only an error while current matters.
            if (run === mine) Log.w(tag, "logcat read failed", e)
        } finally {
            open.close()
            synchronized(lock) { if (current === open) current = null }
        }

        if (produced == 0 && unparsed != null) lastError = unparsed
        return produced
    }

    private fun announce() {
        live.tryEmit(LiveItem.Status(status()))
    }

    private sealed interface LiveItem {
        class Line(val line: LogLineDto) : LiveItem
        class Status(val status: LogcatStatusDto) : LiveItem
    }

    companion object {
        /** Lines one browser may fall behind by before it loses the oldest. */
        private const val STREAM_QUEUE = 8_192

        /** Most lines in one event. */
        private const val BATCH = 500

        private const val HEARTBEAT_MS = 15_000L

        private const val MAX_FAILURES = 5
        private const val RETRY_BASE_MS = 500L
    }
}
