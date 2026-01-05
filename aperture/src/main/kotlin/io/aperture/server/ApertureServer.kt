package io.aperture.server

import android.util.Log
import io.aperture.ApertureConfig
import io.aperture.data.repository.TransactionRepository
import io.aperture.server.dto.*
import io.aperture.util.HeadersSerializer
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Ktor-based web server for Aperture
 * Implements FR-WEB-001 through FR-WEB-014
 */
class ApertureServer(
    private val context: android.content.Context,
    private val repository: TransactionRepository,
    private val config: ApertureConfig,
    private val authToken: String?
) {
    private val tag = "ApertureServer"
    private var server: NettyApplicationEngine? = null
    private val eventFlow = MutableSharedFlow<ServerEvent>(replay = 0, extraBufferCapacity = 100)

    /**
     * Start the web server (FR-WEB-002)
     */
    fun start() {
        if (server != null) {
            Log.w(tag, "Server already running")
            return
        }

        val host = if (config.localhostOnly) "127.0.0.1" else "0.0.0.0"

        server = embeddedServer(Netty, port = config.port, host = host) {
            configureServer()
        }.start(wait = false)

        // Start monitoring for real-time updates
        startEventMonitoring()

        Log.i(tag, "Server started on $host:${config.port}")
    }

    /**
     * Stop the web server
     */
    fun stop() {
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
    private fun Application.configureServer() {
        // Install plugins
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        // CORS support (FR-WEB-007)
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
        }

        // Note: SSE support is built into ktor-server-core, no plugin needed

        // Routing
        routing {
            // Authentication interceptor
            if (authToken != null) {
                intercept(ApplicationCallPipeline.Call) {
                    val path = call.request.path()
                    if (!path.startsWith("/api")) {
                        // Allow UI access without auth
                        return@intercept
                    }

                    val token = call.request.header("Authorization")?.removePrefix("Bearer ")
                    if (token != authToken) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                            error = "Unauthorized",
                            message = "Invalid or missing authentication token"
                        ))
                        finish()
                    }
                }
            }

            // Serve main HTML page
            get("/") {
                call.respondText(getWebUI(), ContentType.Text.Html)
            }

            // API routes
            route("/api") {
                configureTransactionRoutes()
                configureStatsRoutes()
                configureSSERoutes()
            }
        }
    }

    /**
     * Configure transaction-related routes
     */
    private fun Route.configureTransactionRoutes() {
        route("/transactions") {
            // GET /api/transactions - Get all transactions with filters
            get {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val search = call.request.queryParameters["search"]
                val method = call.request.queryParameters["method"]
                val status = call.request.queryParameters["status"]?.toIntOrNull()

                val transactions = when {
                    search != null -> repository.searchByUrl(search, limit.coerceAtMost(500), offset)
                    method != null -> repository.filterByMethod(method, limit.coerceAtMost(500), offset)
                    status != null -> repository.filterByStatusCode(status, limit.coerceAtMost(500), offset)
                    else -> repository.getAll(limit.coerceAtMost(500), offset)
                }

                val total = repository.getCount()

                call.respond(TransactionListResponse(
                    transactions = transactions.map { it.toDto() },
                    total = total,
                    limit = limit,
                    offset = offset
                ))
            }

            // GET /api/transactions/{id} - Get single transaction
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Bad Request",
                        message = "Invalid transaction ID"
                    ))
                    return@get
                }

                val transaction = repository.getById(id)
                if (transaction == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(
                        error = "Not Found",
                        message = "Transaction not found"
                    ))
                    return@get
                }

                call.respond(transaction.toDto())
            }

            // PUT /api/transactions/{id}/mock - Enable/disable mock
            put("/{id}/mock") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Bad Request",
                        message = "Invalid transaction ID"
                    ))
                    return@put
                }

                val request = call.receive<UpdateMockStatusRequest>()
                repository.setMockEnabled(id, request.enabled)

                val updated = repository.getById(id)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(
                        error = "Not Found",
                        message = "Transaction not found"
                    ))
                    return@put
                }

                eventFlow.emit(ServerEvent.TransactionUpdated(updated.toDto()))
                call.respond(updated.toDto())
            }

            // PUT /api/transactions/{id}/response - Update mock response
            put("/{id}/response") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Bad Request",
                        message = "Invalid transaction ID"
                    ))
                    return@put
                }

                val request = call.receive<UpdateMockResponseRequest>()

                // Validate status code
                if (request.statusCode !in 100..599) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Bad Request",
                        message = "Invalid status code"
                    ))
                    return@put
                }

                // Serialize headers
                val headersJson = request.headers?.let {
                    HeadersSerializer.serializeMap(it)
                }

                repository.updateMockResponse(
                    id = id,
                    responseCode = request.statusCode,
                    headers = headersJson,
                    body = request.body
                )

                val updated = repository.getById(id)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(
                        error = "Not Found",
                        message = "Transaction not found"
                    ))
                    return@put
                }

                eventFlow.emit(ServerEvent.TransactionUpdated(updated.toDto()))
                call.respond(updated.toDto())
            }

            // DELETE /api/transactions/{id} - Delete single transaction
            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Bad Request",
                        message = "Invalid transaction ID"
                    ))
                    return@delete
                }

                repository.deleteById(id)
                eventFlow.emit(ServerEvent.TransactionDeleted(id))
                call.respond(HttpStatusCode.NoContent)
            }

            // DELETE /api/transactions - Delete all transactions
            delete {
                repository.deleteAll()
                eventFlow.emit(ServerEvent.AllTransactionsDeleted)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    /**
     * Configure stats route
     */
    private fun Route.configureStatsRoutes() {
        get("/stats") {
            val stats = repository.getStats()
            call.respond(StatsResponse(
                totalTransactions = stats.totalTransactions,
                mockedTransactions = stats.mockedTransactions,
                failedTransactions = stats.failedTransactions,
                averageDuration = stats.averageDuration
            ))
        }
    }

    /**
     * Configure Server-Sent Events route for real-time updates
     * Implements SSE manually using Ktor's respondTextWriter
     */
    private fun Route.configureSSERoutes() {
        get("/stream") {
            // Set SSE headers
            call.response.cacheControl(io.ktor.http.CacheControl.NoCache(null))
            call.response.header("Content-Type", "text/event-stream")
            call.response.header("Connection", "keep-alive")
            call.response.header("X-Accel-Buffering", "no") // Disable nginx buffering

            try {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    // Send initial connection established message
                    write("event: connected\n")
                    write("data: {\"status\":\"connected\"}\n\n")
                    flush()

                    // Collect events from the flow and send to client
                    eventFlow.collect { event ->
                        try {
                            when (event) {
                                is ServerEvent.NewTransaction -> {
                                    write("event: new_transaction\n")
                                    write("data: ${Json.encodeToString(TransactionDto.serializer(), event.transaction)}\n\n")
                                }
                                is ServerEvent.TransactionUpdated -> {
                                    write("event: updated_transaction\n")
                                    write("data: ${Json.encodeToString(TransactionDto.serializer(), event.transaction)}\n\n")
                                }
                                is ServerEvent.TransactionDeleted -> {
                                    write("event: deleted_transaction\n")
                                    write("data: {\"id\": ${event.id}}\n\n")
                                }
                                is ServerEvent.AllTransactionsDeleted -> {
                                    write("event: all_deleted\n")
                                    write("data: {}\n\n")
                                }
                            }
                            flush()
                        } catch (e: Exception) {
                            Log.e(tag, "Error sending SSE event", e)
                            // Client likely disconnected
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

    /**
     * Monitor database for changes and emit SSE events
     */
    private fun startEventMonitoring() {
        CoroutineScope(Dispatchers.IO).launch {
            repository.getAllAsFlow()
                .map { it.firstOrNull() }
                .distinctUntilChanged()
                .collect { latest ->
                    latest?.let {
                        eventFlow.emit(ServerEvent.NewTransaction(it.toDto()))
                    }
                }
        }
    }

    /**
     * Get the embedded web UI HTML from assets
     */
    private fun getWebUI(): String {
        return try {
            // Load from assets using Android AssetManager
            context.assets.open("index.html").use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load web UI from assets", e)
            getBasicUI()
        }
    }

    private fun getBasicUI(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Aperture - Network Inspector</title>
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
                <h1>⚠️ Aperture Network Inspector</h1>
                <p class="error">Web UI could not be loaded from assets.</p>
                <p class="info">Check Logcat for details. The assets/index.html file may be missing.</p>
                <p class="info">API endpoints are still available at /api/transactions</p>
            </body>
            </html>
        """.trimIndent()
    }
}

/**
 * Server-Sent Events types
 */
sealed class ServerEvent {
    data class NewTransaction(val transaction: TransactionDto) : ServerEvent()
    data class TransactionUpdated(val transaction: TransactionDto) : ServerEvent()
    data class TransactionDeleted(val id: Long) : ServerEvent()
    object AllTransactionsDeleted : ServerEvent()
}
