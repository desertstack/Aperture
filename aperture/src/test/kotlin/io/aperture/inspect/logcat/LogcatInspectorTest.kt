package io.aperture.inspect.logcat

import io.aperture.ApertureConfig
import io.aperture.server.ApertureBus
import io.aperture.server.ApertureServer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The stream is what the Logcat panel draws from. It must carry only this process's lines, in
 * order, each with a number the browser can resume from, and a resume from another run of the
 * app must not be taken for one from this run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogcatInspectorTest {

    private val pid = 4321
    private val source = FakeSource()
    private val inspector = LogcatInspector(source = source, pid = pid, capacity = 100)

    @After
    fun tearDown() {
        inspector.stop()
    }

    private fun ApplicationTestBuilder.install() {
        val server = ApertureServer(
            context = RuntimeEnvironment.getApplication(),
            config = ApertureConfig(port = 8080),
            authToken = null,
            bus = ApertureBus(),
            modules = listOf(inspector)
        )
        application {
            val app: Application = this
            with(server) { app.configureServer() }
        }
    }

    private fun line(n: Int, level: Char = 'I', pid: Int = this.pid) =
        "09-28 14:03:%02d.000  $pid  $pid $level Tag     : message $n".format(n % 60)

    /**
     * Run the stream into a string until an event of [type] is complete, then stop it, and
     * return that event's id and data.
     *
     * The stream never ends on its own, and Ktor's test engine does not fail a write after the
     * client leaves, so the stream is driven directly rather than through a test request.
     */
    private fun firstEvent(type: String, after: Long = 0L): Pair<String?, String> = runBlocking {
        val out = StringWriter()
        val job = launch(Dispatchers.IO) { inspector.stream(out, after) }
        try {
            withTimeout(5_000) {
                var found = parse(out.toString(), type)
                while (found == null) {
                    delay(10)
                    found = parse(out.toString(), type)
                }
                found
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun parse(text: String, type: String): Pair<String?, String>? {
        for (block in text.split("\n\n").dropLast(1)) {
            val fields = block.lines()
            if (fields.none { it == "event: $type" }) continue
            val id = fields.firstOrNull { it.startsWith("id: ") }?.removePrefix("id: ")
            val data = fields.first { it.startsWith("data: ") }.removePrefix("data: ")
            return id to data
        }
        return null
    }

    /** Wait until the reader has taken in [count] lines. */
    private fun awaitLines(count: Long) {
        val deadline = System.currentTimeMillis() + 5_000
        while (inspector.status().lastSeq < count) {
            check(System.currentTimeMillis() < deadline) { "reader took in ${inspector.status().lastSeq} of $count" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the panel is listed with the other inspectors`() = testApplication {
        install()

        val body = client.get("/api/capabilities") { header(HttpHeaders.Host, "127.0.0.1") }.bodyAsText()

        assertTrue(body, body.contains("\"id\": \"logcat\""))
        assertTrue(body, body.contains("\"writable\": false"))
    }

    @Test
    fun `nothing reads the log until a browser asks`() = testApplication {
        install()

        val body = client.get("/api/logcat") { header(HttpHeaders.Host, "127.0.0.1") }.bodyAsText()

        assertTrue(body, body.contains("\"reading\": false"))
        assertEquals(0, source.opens)
    }

    @Test
    fun `the stream carries this process's lines and no other`() {
        source.push("--------- beginning of main")
        source.push(line(1, 'D'))
        source.push(line(2, 'E', pid = 999))
        source.push(line(3, 'W'))
        inspector.ensureReading()
        awaitLines(2)

        val (id, data) = firstEvent("logcat_lines")

        assertTrue(data, data.contains("message 1"))
        assertTrue(data, data.contains("message 3"))
        assertFalse("a line from another process: $data", data.contains("message 2"))
        assertTrue(data, data.contains("\"level\":\"W\""))
        assertTrue("id carries the line number: $id", id!!.endsWith(":2"))
    }

    @Test
    fun `a resume from this run gives only newer lines`() {
        (1..3).forEach { source.push(line(it)) }
        inspector.ensureReading()
        awaitLines(3)
        val session = inspector.status().session

        val (_, data) = firstEvent("logcat_lines", after = inspector.resumePoint("$session:2"))

        assertFalse(data, data.contains("message 1"))
        assertFalse(data, data.contains("message 2"))
        assertTrue(data, data.contains("message 3"))
    }

    @Test
    fun `a resume from another run starts from the beginning`() {
        assertEquals(0L, inspector.resumePoint("otherrun:40"))
        assertEquals(0L, inspector.resumePoint(null))
        assertEquals(0L, inspector.resumePoint("garbage"))
        assertEquals(40L, inspector.resumePoint("${inspector.status().session}:40"))
    }

    @Test
    fun `lines that arrive after the browser connects are sent live`() {
        inspector.ensureReading()

        // Push once the stream is open, from outside the request.
        Thread {
            Thread.sleep(300)
            source.push(line(7, 'E'))
        }.start()

        val (_, data) = firstEvent("logcat_lines")

        assertTrue(data, data.contains("message 7"))
        assertTrue(data, data.contains("\"level\":\"E\""))
    }

    @Test
    fun `stopping the server ends the logcat process`() {
        inspector.ensureReading()
        val deadline = System.currentTimeMillis() + 5_000
        while (source.opens == 0) {
            check(System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }

        inspector.stop()

        assertTrue("process closed", source.closed.await())
    }

    /** Stands in for a logcat process: lines go in by [push] and wait there until read. */
    private class FakeSource : LogSource {
        private val queue = LinkedBlockingQueue<String>()
        @Volatile var opens = 0
        val closed = Latch()

        fun push(line: String) = queue.put(line)

        override fun open(pid: Int, resumeAfter: String?): OpenLog {
            opens++
            val reader = object : BufferedReader(StringReader("")) {
                override fun readLine(): String? {
                    val next = queue.take()
                    return if (next === EOF) null else next
                }
            }
            return OpenLog(reader) {
                queue.put(EOF)
                closed.release()
            }
        }

        companion object {
            val EOF = String(charArrayOf('\u0000'))
        }
    }

    private class Latch {
        private val latch = java.util.concurrent.CountDownLatch(1)
        fun release() = latch.countDown()
        fun await(): Boolean = latch.await(5, TimeUnit.SECONDS)
    }
}
