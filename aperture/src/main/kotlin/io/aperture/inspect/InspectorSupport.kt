package io.aperture.inspect

import android.content.Context
import io.aperture.ApertureConfig
import io.aperture.server.ApertureBus
import io.aperture.server.dto.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * What every inspector needs: the host app's context, the settings, and the live-event bus.
 */
internal class InspectorContext(
    val androidContext: Context,
    val config: ApertureConfig,
    val bus: ApertureBus
)

/**
 * Where inspectors do their blocking work.
 *
 * Ktor answers requests on a bounded pool inside the host app's process, and the live-event
 * stream already holds one of those threads for as long as a browser tab stays open. A table
 * scan or a large file read on a request thread would starve that pool and slow the app down.
 * Two threads is the whole budget for inspection, whatever the browser asks for.
 */
internal object InspectorDispatchers {
    @OptIn(ExperimentalCoroutinesApi::class)
    val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)
}

/** How long one inspection request may take before Aperture gives up on it. */
internal const val INSPECT_TIMEOUT_MS = 10_000L

/**
 * Run blocking inspection work off the request thread, with a deadline.
 *
 * @throws TimeoutCancellationException when the work outruns [INSPECT_TIMEOUT_MS].
 */
internal suspend fun <T> inspectIo(
    timeoutMs: Long = INSPECT_TIMEOUT_MS,
    block: suspend () -> T
): T = withContext(InspectorDispatchers.io) {
    withTimeout(timeoutMs) { block() }
}

/**
 * Refuse a write unless the host app asked for writes.
 *
 * This is the boundary. The console hides its edit controls when writes are off, but that is a
 * courtesy; a request made by hand has to be turned away here.
 *
 * @return true when the caller may continue.
 */
internal suspend fun ApplicationCall.allowsWrites(config: ApertureConfig): Boolean {
    if (config.allowWrites) return true
    respond(
        HttpStatusCode.Forbidden,
        ErrorResponse(
            error = "Writes disabled",
            message = "Aperture is read-only. Set allowWrites = true in ApertureConfig to edit app state."
        )
    )
    return false
}

/** Answer 400 with a reason. */
internal suspend fun ApplicationCall.badRequest(message: String) {
    respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Bad Request", message = message))
}

/** Answer 404 with a reason. */
internal suspend fun ApplicationCall.notFound(message: String) {
    respond(HttpStatusCode.NotFound, ErrorResponse(error = "Not Found", message = message))
}

/** Answer 409 with a reason. Used when the state moved under an edit. */
internal suspend fun ApplicationCall.conflict(message: String) {
    respond(HttpStatusCode.Conflict, ErrorResponse(error = "Conflict", message = message))
}
