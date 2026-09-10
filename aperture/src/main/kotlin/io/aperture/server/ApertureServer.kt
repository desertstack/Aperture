package io.aperture.server

import android.util.Log
import io.aperture.ApertureConfig
import io.aperture.inspect.InspectorModule
import io.aperture.server.dto.CapabilitiesResponse
import io.aperture.server.dto.ErrorResponse
import io.aperture.server.dto.InspectorDto
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The web console: an embedded Ktor server that answers on the device.
 *
 * The server owns the plumbing every inspector shares — plugins, authentication, the live
 * event stream, the static console — and nothing about any one domain. Each domain arrives as
 * an [InspectorModule] and hangs its own routes under `/api/{id}`.
 */
class ApertureServer internal constructor(
    private val context: android.content.Context,
    private val config: ApertureConfig,
    private val authToken: String?,
    private val bus: ApertureBus,
    private val modules: List<InspectorModule>
) {
    private val tag = "ApertureServer"

    // Read from the caller thread by isRunning(), written under the lock by start() and stop().
    @Volatile
    private var server: NettyApplicationEngine? = null

    /** Assets are read from the APK once and kept, not re-read per request. */
    private val assetCache = ConcurrentHashMap<String, CachedAsset>()

    private val activeModules: List<InspectorModule> by lazy {
        modules.filter {
            try {
                it.isAvailable()
            } catch (e: Exception) {
                Log.w(tag, "Inspector '${it.id}' is unavailable", e)
                false
            }
        }
    }

    /**
     * Start the web server (FR-WEB-002)
     *
     * Blocks the calling thread until Netty binds the port, so call it off the main thread.
     * Synchronized because the foreground service and the in-process fallback can both ask
     * for a start, and a second bind on the same port fails.
     */
    @Synchronized
    fun start() {
        if (server != null) {
            Log.w(tag, "Server already running")
            return
        }

        val host = if (config.localhostOnly) "127.0.0.1" else "0.0.0.0"

        server = embeddedServer(Netty, port = config.port, host = host) {
            configureServer()
        }.start(wait = false)

        for (module in activeModules) {
            try {
                module.start()
            } catch (e: Exception) {
                Log.e(tag, "Inspector '${module.id}' failed to start", e)
            }
        }

        Log.i(tag, "Server started on $host:${config.port}")
    }

    /**
     * Stop the web server
     *
     * Waits for Netty to wind down, up to the grace period, so call it off the main thread.
     */
    @Synchronized
    fun stop() {
        for (module in activeModules) {
            try {
                module.stop()
            } catch (e: Exception) {
                Log.e(tag, "Inspector '${module.id}' failed to stop", e)
            }
        }
        server?.stop(1000, 2000)
        server = null
        Log.i(tag, "Server stopped")
    }

    /**
     * Check if server is running
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * Configure Ktor application
     */
    internal fun Application.configureServer() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                // Send every field, default or not. Otherwise a false flag arrives as a
                // missing key and the console has to guess what its absence meant.
                encodeDefaults = true
            })
        }

        // Nothing here may reach Ktor's default handler. Reading preferences, files and host
        // databases throws far more readily than a Room query does, and a bodyless 500 tells
        // the console nothing.
        install(StatusPages) {
            exception<TimeoutCancellationException> { call, _ ->
                call.respond(
                    HttpStatusCode.GatewayTimeout,
                    ErrorResponse("Timeout", "The device took too long to answer that request.")
                )
            }
            exception<Throwable> { call, cause ->
                Log.w(tag, "Request failed: ${call.request.path()}", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        error = cause::class.java.simpleName,
                        message = cause.message ?: "The device could not answer that request."
                    )
                )
            }
        }

        // The console is 44 KB of text before this feature and more after it. Over ADB or WiFi
        // that is worth compressing.
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 0.9 }
            minimumSize(512)
            // Server-Sent Events must not be buffered into compression blocks.
            matchContentType(
                ContentType.Text.Html,
                ContentType.Text.CSS,
                ContentType.Text.Plain,
                ContentType.Application.JavaScript,
                ContentType.Application.Json,
                ContentType.Image.SVG
            )
        }

        // Same-origin only. The console is served by this server, so it never needs a
        // cross-origin grant. `anyHost()` used to publish captured traffic to any page the
        // developer happened to have open; over app databases that is worse.
        install(CORS) {
            allowOrigins { origin -> isLoopbackOrigin(origin) }
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
        }

        // Note: SSE support is built into ktor-server-core, no plugin needed

        routing {
            intercept(ApplicationCallPipeline.Call) {
                val path = call.request.path()
                if (!path.startsWith("/api")) return@intercept

                // A browser resolves an attacker's domain to this device's address, then reads
                // the response. Requiring a literal address, or localhost, defeats that: an
                // attacker cannot put an IP literal in a domain name they control.
                if (!isAllowedHost(call.request.host())) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            error = "Forbidden",
                            message = "Reach Aperture by IP address or localhost, not by host name."
                        )
                    )
                    finish()
                    return@intercept
                }

                if (authToken != null && !call.hasValidToken()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(
                            error = "Unauthorized",
                            message = "Invalid or missing authentication token"
                        )
                    )
                    finish()
                }
            }

            get("/") {
                respondAsset(INDEX_ASSET, ContentType.Text.Html)
            }

            // The console's own files. Mounted under /ui so no tailcard ever competes with /api.
            get("/ui/{path...}") {
                val requested = call.parameters.getAll("path").orEmpty().joinToString("/")
                if (requested.isEmpty() || requested.contains("..")) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                respondAsset("$UI_ASSET_ROOT/$requested", contentTypeFor(requested))
            }

            route("/api") {
                get("/capabilities") { call.respond(capabilities()) }

                configureSSERoutes()

                for (module in activeModules) {
                    route("/${module.id}") { module.register(this) }
                }
            }
        }
    }

    /**
     * Live events, as one stream the whole console shares.
     *
     * A panel asks for the channels it draws with `?channels=network,prefs`. Without the
     * parameter it sees everything, which is what the older console expects.
     */
    private fun Route.configureSSERoutes() {
        get("/stream") {
            val wanted = call.request.queryParameters["channels"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }

            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.header("Content-Type", "text/event-stream")
            call.response.header("Connection", "keep-alive")
            call.response.header("X-Accel-Buffering", "no") // Disable nginx buffering

            try {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    write("event: connected\n")
                    write("data: {\"status\":\"connected\"}\n\n")
                    flush()

                    bus.events.collect { event ->
                        if (wanted != null && event.channel !in wanted) return@collect
                        try {
                            write("event: ${event.type}\n")
                            write("data: ${event.json}\n\n")
                            flush()
                        } catch (e: Exception) {
                            Log.d(tag, "SSE write failed, client gone", e)
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "SSE connection closed: ${e.message}")
                // Connection closed, this is normal
            }
        }
    }

    private fun capabilities(): CapabilitiesResponse {
        val packageName = context.packageName
        val appVersion = try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($code)"
        } catch (e: Exception) {
            "unknown"
        }

        return CapabilitiesResponse(
            apertureVersion = APERTURE_VERSION,
            appId = packageName,
            appVersion = appVersion,
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            androidApi = android.os.Build.VERSION.SDK_INT,
            sqliteVersion = sqliteVersion(),
            allowWrites = config.allowWrites,
            requireAuth = config.requireAuth,
            exposedOnNetwork = !config.localhostOnly && !config.requireAuth,
            inspectors = activeModules.map {
                InspectorDto(
                    id = it.id,
                    label = it.label,
                    icon = it.icon,
                    writable = it.writable && config.allowWrites
                )
            }
        )
    }

    /**
     * Which SQLite this device ships. Feature availability in the database inspector turns on
     * it, and it varies from 3.8.6 on API 21 to 3.44 and up on recent releases.
     */
    private fun sqliteVersion(): String = try {
        android.database.sqlite.SQLiteDatabase.create(null).use { db ->
            db.rawQuery("SELECT sqlite_version()", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
            }
        }
    } catch (e: Exception) {
        "unknown"
    }

    private fun ApplicationCall.hasValidToken(): Boolean {
        val expected = authToken ?: return true
        // EventSource cannot set a header, so the stream accepts the token as a parameter too.
        val presented = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
            ?: request.queryParameters["token"]
            ?: return false
        return MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.respondAsset(
        path: String,
        contentType: ContentType
    ) {
        val asset = loadAsset(path)
        if (asset == null) {
            if (path == INDEX_ASSET) {
                call.respondText(getBasicUI(), ContentType.Text.Html)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
            return
        }

        if (call.request.header(HttpHeaders.IfNoneMatch)?.trim('"') == asset.etag) {
            call.respond(HttpStatusCode.NotModified)
            return
        }

        call.response.header(HttpHeaders.ETag, "\"${asset.etag}\"")
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondBytes(asset.bytes, contentType)
    }

    private fun loadAsset(path: String): CachedAsset? {
        assetCache[path]?.let { return it }
        return try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            val etag = MessageDigest.getInstance("SHA-1")
                .digest(bytes)
                .take(10)
                .joinToString("") { "%02x".format(it) }
            CachedAsset(bytes, etag).also { assetCache[path] = it }
        } catch (e: Exception) {
            Log.w(tag, "Asset not found: $path")
            null
        }
    }

    private fun getBasicUI(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Aperture</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        max-width: 800px;
                        margin: 50px auto;
                        padding: 20px;
                        text-align: center;
                    }
                    .error { color: #f44336; }
                    .info { color: #666; margin-top: 20px; }
                </style>
            </head>
            <body>
                <h1>Aperture</h1>
                <p class="error">The console could not be loaded from the app's assets.</p>
                <p class="info">Check Logcat for details.</p>
                <p class="info">The API is still available at /api/capabilities</p>
            </body>
            </html>
        """.trimIndent()
    }

    private class CachedAsset(val bytes: ByteArray, val etag: String)

    companion object {
        internal const val APERTURE_VERSION = "1.2.0"
        private const val UI_ASSET_ROOT = "ui"
        private const val INDEX_ASSET = "ui/index.html"

        /**
         * Whether a Host header may reach the API.
         *
         * Loopback names and bare addresses pass. A registered domain does not, because that
         * is the only thing a rebinding attacker can point at this device.
         */
        internal fun isAllowedHost(host: String): Boolean {
            var name = host.trim().lowercase()
            if (name.startsWith("[")) {
                // Bracketed IPv6, as in [::1]:8080
                val end = name.indexOf(']')
                if (end < 0) return false
                name = name.substring(1, end)
            } else if (name.count { it == ':' } == 1) {
                // A single colon is a port. More than one means a bare IPv6 address.
                name = name.substringBefore(':')
            }
            if (name.isEmpty()) return false
            if (name == "localhost" || name == "::1") return true
            return isIpLiteral(name)
        }

        private fun isIpLiteral(name: String): Boolean {
            if (name.contains(':')) {
                // IPv6: hex groups and separators only, never a letter outside a-f.
                return name.all { it.isDigit() || it in "abcdef:." }
            }
            val parts = name.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
                    part.toInt() in 0..255
            }
        }

        /** Cross-origin calls are allowed from a developer's own machine and nowhere else. */
        internal fun isLoopbackOrigin(origin: String): Boolean {
            val withoutScheme = origin.substringAfter("://", origin)
            return isAllowedHost(withoutScheme)
        }

        internal fun contentTypeFor(path: String): ContentType = when (path.substringAfterLast('.', "")) {
            "html" -> ContentType.Text.Html
            "css" -> ContentType.Text.CSS
            "js", "mjs" -> ContentType.Application.JavaScript
            "json" -> ContentType.Application.Json
            "svg" -> ContentType.Image.SVG
            "png" -> ContentType.Image.PNG
            "ico" -> ContentType("image", "x-icon")
            "woff2" -> ContentType("font", "woff2")
            "map" -> ContentType.Application.Json
            else -> ContentType.Text.Plain
        }
    }
}
