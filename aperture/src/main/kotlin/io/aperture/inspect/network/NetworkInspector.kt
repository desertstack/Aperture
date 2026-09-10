package io.aperture.inspect.network

import android.util.Log
import io.aperture.ApertureConfig
import io.aperture.data.repository.TransactionRepository
import io.aperture.inspect.InspectorModule
import io.aperture.server.ApertureBus
import io.aperture.server.BusEvent
import io.aperture.server.dto.TransactionDto
import io.aperture.server.dto.TransactionListResponse
import io.aperture.server.dto.TransactionSummaryDto
import io.aperture.server.dto.StatsResponse
import io.aperture.server.dto.UpdateMockResponseRequest
import io.aperture.server.dto.UpdateMockStatusRequest
import io.aperture.server.dto.toDto
import io.aperture.inspect.badRequest
import io.aperture.inspect.notFound
import io.aperture.util.HeadersSerializer
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * HTTP traffic captured by the OkHttp interceptor.
 *
 * This is the domain Aperture started with. The routes moved out of [io.aperture.server.ApertureServer]
 * unchanged, so the paths and the live-event names are the same as they were.
 */
internal class NetworkInspector(
    private val repository: TransactionRepository,
    private val config: ApertureConfig,
    private val bus: ApertureBus
) : InspectorModule {

    override val id = "network"
    override val label = "Network"
    override val icon = "activity"

    /** Mocking edits captured traffic, not app state, so it stands outside `allowWrites`. */
    override val writable = false

    private val tag = "ApertureNetwork"
    private var scope: CoroutineScope? = null

    override fun isAvailable() = true

    override fun register(route: Route) {
        route.configureTransactionRoutes()
        route.configureStatsRoute()
    }

    /**
     * Watch the newest captured transaction and announce it.
     *
     * Updates to older rows do not come through here. The routes that change a row announce it
     * themselves, because they already hold the whole record.
     */
    override fun start() {
        stop()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launch {
            try {
                repository.getLatestSummaryAsFlow()
                    .distinctUntilChanged()
                    .collect { latest ->
                        latest?.let { bus.emit(newTransaction(it.toDto())) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing here may reach the default handler: this coroutine runs inside the
                // host app, and an uncaught throw here kills it.
                Log.e(tag, "Stopped watching the database for new transactions", e)
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
    }

    private fun Route.configureTransactionRoutes() {
        route("/transactions") {
            // GET /transactions - list, filtered
            get {
                val params = call.request.queryParameters
                val limit = (params["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 500)
                val offset = (params["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
                val search = params["search"]?.takeIf { it.isNotBlank() }
                val method = params["method"]?.takeIf { it.isNotBlank() }
                val statusClass = params["status"]?.takeIf { it.isNotBlank() }

                // Summaries, not whole transactions. The list shows no bodies, and loading up
                // to 500 of them would hold hundreds of megabytes in the host app.
                val page = repository.getSummaries(
                    limit = limit,
                    offset = offset,
                    search = search,
                    method = method,
                    statusClass = statusClass
                )

                call.respond(
                    TransactionListResponse(
                        transactions = page.items.map { it.toDto() },
                        total = page.total,
                        limit = limit,
                        offset = offset
                    )
                )
            }

            // GET /transactions/{id} - one whole record, bodies included
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.badRequest("Invalid transaction ID")
                    return@get
                }

                val transaction = repository.getById(id)
                if (transaction == null) {
                    call.notFound("Transaction not found")
                    return@get
                }

                call.respond(transaction.toDto())
            }

            // PUT /transactions/{id}/mock - turn mocking on or off
            put("/{id}/mock") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.badRequest("Invalid transaction ID")
                    return@put
                }

                val request = call.receive<UpdateMockStatusRequest>()
                repository.setMockEnabled(id, request.enabled)

                val updated = repository.getById(id)
                if (updated == null) {
                    call.notFound("Transaction not found")
                    return@put
                }

                bus.emit(updatedTransaction(updated.toDto()))
                call.respond(updated.toDto())
            }

            // PUT /transactions/{id}/response - edit the mocked response
            put("/{id}/response") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.badRequest("Invalid transaction ID")
                    return@put
                }

                val request = call.receive<UpdateMockResponseRequest>()
                if (request.statusCode !in 100..599) {
                    call.badRequest("Invalid status code")
                    return@put
                }

                repository.updateMockResponse(
                    id = id,
                    responseCode = request.statusCode,
                    headers = request.headers?.let { HeadersSerializer.serializeMap(it) },
                    body = request.body
                )

                val updated = repository.getById(id)
                if (updated == null) {
                    call.notFound("Transaction not found")
                    return@put
                }

                bus.emit(updatedTransaction(updated.toDto()))
                call.respond(updated.toDto())
            }

            // DELETE /transactions/{id}/mock - declared before /{id}, or it never matches
            delete("/{id}/mock") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.badRequest("Invalid transaction ID")
                    return@delete
                }

                repository.clearMock(id)

                val updated = repository.getById(id)
                if (updated == null) {
                    call.notFound("Transaction not found")
                    return@delete
                }

                bus.emit(updatedTransaction(updated.toDto()))
                call.respond(updated.toDto())
            }

            // DELETE /transactions/{id}
            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.badRequest("Invalid transaction ID")
                    return@delete
                }

                repository.deleteById(id)
                bus.emit(BusEvent(CHANNEL, "deleted_transaction", """{"id":$id}"""))
                call.respond(HttpStatusCode.NoContent)
            }

            // DELETE /transactions
            delete {
                repository.deleteAll()
                bus.emit(BusEvent(CHANNEL, "all_deleted", "{}"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    private fun Route.configureStatsRoute() {
        get("/stats") {
            val stats = repository.getStats()
            call.respond(
                StatsResponse(
                    totalTransactions = stats.totalTransactions,
                    mockedTransactions = stats.mockedTransactions,
                    failedTransactions = stats.failedTransactions,
                    averageDuration = stats.averageDuration
                )
            )
        }
    }

    companion object {
        const val CHANNEL = "network"

        fun newTransaction(dto: TransactionSummaryDto) = BusEvent(
            channel = CHANNEL,
            type = "new_transaction",
            json = ApertureBus.json.encodeToString(TransactionSummaryDto.serializer(), dto)
        )

        fun updatedTransaction(dto: TransactionDto) = BusEvent(
            channel = CHANNEL,
            type = "updated_transaction",
            json = ApertureBus.json.encodeToString(TransactionDto.serializer(), dto)
        )
    }
}
