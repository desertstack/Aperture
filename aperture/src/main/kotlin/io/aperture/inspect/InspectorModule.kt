package io.aperture.inspect

import io.ktor.server.routing.Route

/**
 * One domain the web console can show.
 *
 * The four storage domains are too different for a shared read/write abstraction, so this
 * interface covers only what they have in common: a name, whether they can run here, and a
 * place to hang routes. Everything else belongs to the module.
 *
 * A module registers under `/api/{id}`. That prefix matters: the authentication interceptor
 * guards `/api` by path, so a route outside it is public.
 */
internal interface InspectorModule {

    /** Stable id. It names the route group, the live-event channel and the UI panel. */
    val id: String

    /** What the console calls this domain in its navigation. */
    val label: String

    /** Icon key. The web console maps it to an inline SVG; it is never a file name. */
    val icon: String

    /** True when the domain can change app state at all, before [io.aperture.ApertureConfig.allowWrites]. */
    val writable: Boolean

    /**
     * Whether this module can run on this device, in this app.
     *
     * Called once at startup. A module that needs a class the host app does not ship, or a
     * directory that does not exist, answers false and is left out of the console entirely.
     * It must not throw.
     */
    fun isAvailable(): Boolean

    /** Add this module's routes. The receiver is already scoped to `/api/{id}`. */
    fun register(route: Route)

    /**
     * Begin any background watching this module does, such as following a database for new
     * rows. Called after the server binds its port. It must not throw.
     */
    fun start() {}

    /** Release whatever [start] took. Called when the server stops. It must not throw. */
    fun stop() {}
}
