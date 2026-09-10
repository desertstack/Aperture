package io.aperture.server.dto

import kotlinx.serialization.Serializable

/**
 * What the console asks for before it draws anything.
 *
 * The browser builds its navigation from [inspectors]. It never holds a hardcoded list, so an
 * inspector the host app turned off simply does not appear.
 */
@Serializable
data class CapabilitiesResponse(
    val apertureVersion: String,
    val appId: String,
    val appVersion: String,
    val device: String,
    val androidApi: Int,
    val sqliteVersion: String,
    val allowWrites: Boolean,
    val requireAuth: Boolean,
    /** True when the console answers on the network with no token. Drives the warning chip. */
    val exposedOnNetwork: Boolean,
    val inspectors: List<InspectorDto>
)

@Serializable
data class InspectorDto(
    val id: String,
    val label: String,
    val icon: String,
    val writable: Boolean
)
