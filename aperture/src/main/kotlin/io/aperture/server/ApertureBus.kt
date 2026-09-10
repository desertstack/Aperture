package io.aperture.server

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json

/**
 * One live event on its way to the browser.
 *
 * [json] is already serialized. Server-Sent Events end a frame at the first blank line, so a
 * payload with a newline in it truncates the frame and the browser sees a broken event. Every
 * producer therefore serializes with [ApertureBus.json], which never indents.
 */
internal data class BusEvent(
    val channel: String,
    val type: String,
    val json: String
)

/**
 * The single stream of live events, shared by every inspector.
 *
 * The old event flow lived inside [ApertureServer] with no accessor, so nothing outside the
 * class could publish. Inspectors that watch preferences or database rows need to, so the flow
 * moved here.
 *
 * Emission never suspends. A request handler that suspends on a full buffer would hold a Netty
 * thread inside the host app, and one slow browser tab would stall the app's own traffic. A
 * client that cannot keep up loses the oldest events instead.
 */
internal class ApertureBus {

    private val flow = MutableSharedFlow<BusEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<BusEvent> = flow

    fun emit(event: BusEvent) {
        flow.tryEmit(event)
    }

    fun emit(channel: String, type: String, json: String) {
        emit(BusEvent(channel, type, json))
    }

    companion object {
        /**
         * The serializer every event payload must use.
         *
         * Compact on purpose. See [BusEvent].
         */
        val json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
