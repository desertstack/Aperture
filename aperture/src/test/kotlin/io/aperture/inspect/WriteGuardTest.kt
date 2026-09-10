package io.aperture.inspect

import io.aperture.ApertureConfig
import io.aperture.inspect.datastore.DataStoreInspector
import io.aperture.inspect.db.DatabaseInspector
import io.aperture.inspect.files.FilesInspector
import io.aperture.inspect.prefs.PrefsInspector
import io.aperture.server.ApertureBus
import io.aperture.server.ApertureServer
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Reading is one thing; changing the running app is another. Aperture is read-only until the
 * host app says otherwise, and that refusal has to live on the server. The console hiding its
 * edit buttons is a courtesy, and a request made by hand ignores courtesies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WriteGuardTest {

    private fun ApplicationTestBuilder.install(allowWrites: Boolean) {
        val config = ApertureConfig(port = 8080, allowWrites = allowWrites)
        val bus = ApertureBus()
        val inspectorContext = InspectorContext(RuntimeEnvironment.getApplication(), config, bus)
        val server = ApertureServer(
            context = RuntimeEnvironment.getApplication(),
            config = config,
            authToken = null,
            bus = bus,
            modules = listOf(
                PrefsInspector(inspectorContext),
                FilesInspector(inspectorContext),
                DatabaseInspector(inspectorContext),
                DataStoreInspector(inspectorContext)
            )
        )
        application {
            val app: Application = this
            with(server) { app.configureServer() }
        }
    }

    @Test
    fun `every write is refused when the host app has not allowed writes`() = testApplication {
        install(allowWrites = false)

        val refusals = listOf(
            "PUT /api/prefs/{file}/entry" to
            client.put("/api/prefs/settings/entry") {
                header(HttpHeaders.Host, "127.0.0.1")
                contentType(ContentType.Application.Json)
                setBody("""{"key":"a","type":"string","value":"b"}""")
            },
            "DELETE /api/prefs/{file}/entry" to
                client.delete("/api/prefs/settings/entry?key=a") { header(HttpHeaders.Host, "127.0.0.1") },
            "DELETE /api/prefs/{file}" to
                client.delete("/api/prefs/settings") { header(HttpHeaders.Host, "127.0.0.1") },
            "PUT /api/files/content" to
                client.put("/api/files/content") {
                header(HttpHeaders.Host, "127.0.0.1")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"anything","text":"x"}""")
            },
            "DELETE /api/files" to
                client.delete("/api/files?id=anything") { header(HttpHeaders.Host, "127.0.0.1") },
            "PUT /api/db/{name}/cell" to
                client.put("/api/db/app.db/cell") {
                header(HttpHeaders.Host, "127.0.0.1")
                contentType(ContentType.Application.Json)
                setBody("""{"table":"t","rowId":1,"column":"c","type":"text","value":"x","expectedType":"text","expectedValue":"y"}""")
            },
            "PUT /api/datastore/{name}/entry" to
                client.put("/api/datastore/settings/entry") {
                header(HttpHeaders.Host, "127.0.0.1")
                contentType(ContentType.Application.Json)
                setBody("""{"key":"a","type":"string","value":"b"}""")
            },
            "DELETE /api/datastore/{name}/entry" to
                client.delete("/api/datastore/settings/entry?key=a") { header(HttpHeaders.Host, "127.0.0.1") }
        )

        for ((route, response) in refusals) {
            assertEquals("$route should be refused", HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `a statement that would change data is refused before it runs`() = testApplication {
        install(allowWrites = false)

        val response = client.post("/api/db/app.db/query") {
            header(HttpHeaders.Host, "127.0.0.1")
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"DELETE FROM users"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a statement that damages the app is refused even with writes allowed`() = testApplication {
        install(allowWrites = true)

        for (sql in listOf(
            "ATTACH DATABASE '/sdcard/evil.db' AS evil",
            "VACUUM INTO '/sdcard/copy.db'",
            "PRAGMA journal_mode=DELETE",
            "BEGIN"
        )) {
            val response = client.post("/api/db/app.db/query") {
                header(HttpHeaders.Host, "127.0.0.1")
                contentType(ContentType.Application.Json)
                setBody("""{"sql":${'"'}$sql${'"'}}""")
            }
            assertEquals("$sql should be refused", HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `reading still works with writes off`() = testApplication {
        install(allowWrites = false)

        for (path in listOf("/api/prefs", "/api/files", "/api/db", "/api/datastore")) {
            assertEquals(
                "$path should still be readable",
                HttpStatusCode.OK,
                client.get(path) { header(HttpHeaders.Host, "127.0.0.1") }.status
            )
        }
    }
}
