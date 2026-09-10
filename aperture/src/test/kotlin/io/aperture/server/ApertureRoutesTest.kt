package io.aperture.server

import androidx.room.Room
import io.aperture.ApertureConfig
import io.aperture.data.ApertureDatabase
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.inspect.network.NetworkInspector
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Nothing used to test the server itself: not a route, not a status code, not the token check.
 * Aperture now reads and writes the host app's own storage over that server, so the boundary
 * needs to be held down by tests rather than by reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApertureRoutesTest {

    private lateinit var database: ApertureDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ApertureDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = TransactionRepository(database.transactionDao())

        listOf(
            Triple("GET", 200, "http://example.com/users/1"),
            Triple("POST", 500, "http://example.com/users"),
            Triple("GET", 404, "http://example.com/posts/9")
        ).forEachIndexed { index, (method, code, url) ->
            database.transactionDao().insert(
                HttpTransaction(
                    requestDate = 1_700_000_000_000L + index,
                    method = method,
                    url = url,
                    host = "example.com",
                    path = url.substringAfter("example.com"),
                    scheme = "http",
                    responseCode = code
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun ApplicationTestBuilder.install(config: ApertureConfig, token: String? = null) {
        val bus = ApertureBus()
        val server = ApertureServer(
            context = RuntimeEnvironment.getApplication(),
            config = config,
            authToken = token,
            bus = bus,
            modules = listOf(NetworkInspector(repository, config, bus))
        )
        application {
            val app: Application = this
            with(server) { app.configureServer() }
        }
    }

    @Test
    fun `the console asks what this device offers`() = testApplication {
        install(ApertureConfig(port = 8080))

        val body = client.get("/api/capabilities") {
            header(HttpHeaders.Host, "127.0.0.1")
        }.bodyAsText()

        assertTrue("names the host app: $body", body.contains(RuntimeEnvironment.getApplication().packageName))
        assertTrue("lists the network inspector: $body", body.contains("\"id\": \"network\""))
        assertTrue("reports read-only by default: $body", body.contains("\"allowWrites\": false"))
    }

    @Test
    fun `writes are off until the host app turns them on`() = testApplication {
        install(ApertureConfig(port = 8080))

        val body = client.get("/api/capabilities") { header(HttpHeaders.Host, "127.0.0.1") }.bodyAsText()

        assertTrue(body.contains("\"allowWrites\": false"))
    }

    @Test
    fun `a request that arrives by host name is refused`() = testApplication {
        install(ApertureConfig(port = 8080))

        val response = client.get("/api/capabilities") {
            header(HttpHeaders.Host, "rebind.attacker.example")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `without a token the api says so`() = testApplication {
        install(ApertureConfig(port = 8080, requireAuth = true), token = "secret-token")

        val response = client.get("/api/capabilities") { header(HttpHeaders.Host, "127.0.0.1") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a token in the header opens the api`() = testApplication {
        install(ApertureConfig(port = 8080, requireAuth = true), token = "secret-token")

        val response = client.get("/api/capabilities") {
            header(HttpHeaders.Host, "127.0.0.1")
            header(HttpHeaders.Authorization, "Bearer secret-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a token in the query opens the live stream`() = testApplication {
        // EventSource cannot set a header, so the stream would be unreachable without this.
        install(ApertureConfig(port = 8080, requireAuth = true), token = "secret-token")

        val response = client.get("/api/capabilities?token=secret-token") {
            header(HttpHeaders.Host, "127.0.0.1")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a wrong token stays out`() = testApplication {
        install(ApertureConfig(port = 8080, requireAuth = true), token = "secret-token")

        val response = client.get("/api/capabilities?token=secret-tokes") {
            header(HttpHeaders.Host, "127.0.0.1")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `the list carries no bodies`() = testApplication {
        install(ApertureConfig(port = 8080))

        val body = client.get("/api/network/transactions") { header(HttpHeaders.Host, "127.0.0.1") }.bodyAsText()

        assertFalse("list response leaks a body: $body", body.contains("\"responseBody\""))
        assertTrue(body.contains("\"total\": 3"))
    }

    @Test
    fun `every filter applies at once`() = testApplication {
        install(ApertureConfig(port = 8080))

        // Two GETs and one POST. Only one GET is a 4xx, and only one of those matches "posts".
        val body = client.get("/api/network/transactions?method=GET&status=4xx&search=posts") {
            header(HttpHeaders.Host, "127.0.0.1")
        }.bodyAsText()

        assertTrue("filtered to one row: $body", body.contains("\"total\": 1"))
        assertTrue(body.contains("/posts/9"))
    }

    @Test
    fun `the status class actually filters`() = testApplication {
        install(ApertureConfig(port = 8080))

        val body = client.get("/api/network/transactions?status=5xx") {
            header(HttpHeaders.Host, "127.0.0.1")
        }.bodyAsText()

        assertTrue("only the 500: $body", body.contains("\"total\": 1"))
        assertTrue(body.contains("\"responseCode\": 500"))
    }

    @Test
    fun `the console itself is served`() = testApplication {
        install(ApertureConfig(port = 8080))

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("/ui/js/app.js"))
    }

    @Test
    fun `an asset that is not there is not found`() = testApplication {
        install(ApertureConfig(port = 8080))

        assertEquals(HttpStatusCode.NotFound, client.get("/ui/nope.js").status)
    }

    @Test
    fun `an asset path cannot climb out of the console`() = testApplication {
        install(ApertureConfig(port = 8080))

        assertEquals(HttpStatusCode.NotFound, client.get("/ui/../index.html").status)
    }
}
