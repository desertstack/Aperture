package io.aperture.inspect.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.aperture.inspect.InspectorContext
import io.aperture.inspect.InspectorModule
import io.aperture.inspect.InspectorRegistry
import io.aperture.inspect.allowsWrites
import io.aperture.inspect.badRequest
import io.aperture.inspect.inspectIo
import io.aperture.inspect.notFound
import io.aperture.server.ApertureBus
import io.aperture.server.BusEvent
import io.aperture.server.dto.PrefEntryDto
import io.aperture.server.dto.PrefWriteRequest
import io.aperture.server.dto.PrefWriteResponse
import io.aperture.server.dto.PrefsDetailResponse
import io.aperture.server.dto.PrefsFileDto
import io.aperture.server.dto.PrefsListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * The app's SharedPreferences, read and written live.
 *
 * This is the one domain where reaching in directly is not merely safe but exactly right.
 * `Context.getSharedPreferences` resolves against a static, process-wide cache keyed by file,
 * so Aperture gets the very instance the host app is holding. A write lands in the app's own
 * in-memory map at once, and the app's change listeners fire.
 */
internal class PrefsInspector(
    private val ctx: InspectorContext
) : InspectorModule {

    override val id = "prefs"
    override val label = "Preferences"
    override val icon = "sliders"
    override val writable = true

    private val tag = "AperturePrefs"

    /**
     * Change listeners, held strongly on purpose.
     *
     * SharedPreferencesImpl keeps its listeners in a WeakHashMap. A listener that only the
     * framework refers to is collected at the next GC and the live updates stop, quietly.
     */
    private val listeners = mutableMapOf<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    /**
     * Always. A fresh app has no `shared_prefs` directory yet, and hiding the panel until it
     * writes one would be a puzzle rather than an answer. The list is simply empty.
     */
    override fun isAvailable(): Boolean = true

    override fun stop() {
        synchronized(listeners) {
            for ((name, listener) in listeners) {
                runCatching { open(name)?.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            listeners.clear()
        }
    }

    override fun register(route: Route) {
        route.get { call.respond(PrefsListResponse(listFiles())) }

        route.route("/{file}") {
            get {
                val name = call.parameters["file"]
                if (name.isNullOrBlank()) {
                    call.badRequest("Name a preferences file.")
                    return@get
                }
                val detail = inspectIo { readFile(name) }
                if (detail == null) {
                    call.notFound("No preferences file called $name.")
                    return@get
                }
                watch(name)
                call.respond(detail)
            }

            put("/entry") {
                if (!call.allowsWrites(ctx.config)) return@put
                val name = call.parameters["file"] ?: return@put call.badRequest("Name a preferences file.")
                val request = call.receive<PrefWriteRequest>()

                val prefs = open(name)
                if (prefs == null) {
                    call.notFound("No preferences file called $name.")
                    return@put
                }

                val previous = prefs.all[request.key]?.let { PrefsReader.toDto(request.key, it) }
                val outcome = inspectIo {
                    PrefsReader.write(
                        prefs = prefs,
                        key = request.key,
                        type = request.type,
                        value = request.value,
                        values = request.values,
                        allowTypeChange = request.allowTypeChange
                    )
                }

                when (outcome) {
                    is PrefsReader.WriteOutcome.Rejected -> call.badRequest(outcome.reason)
                    is PrefsReader.WriteOutcome.Ok -> {
                        announce(name, request.key)
                        call.respond(PrefWriteResponse(previous = previous, current = outcome.applied))
                    }
                }
            }

            delete("/entry") {
                if (!call.allowsWrites(ctx.config)) return@delete
                val name = call.parameters["file"] ?: return@delete call.badRequest("Name a preferences file.")
                val key = call.request.queryParameters["key"]
                if (key.isNullOrEmpty()) {
                    call.badRequest("Name the key to remove.")
                    return@delete
                }
                if (PrefsReader.isKeysetKey(key)) {
                    call.badRequest("That key holds encryption key material.")
                    return@delete
                }

                val prefs = open(name)
                if (prefs == null) {
                    call.notFound("No preferences file called $name.")
                    return@delete
                }

                val previous = prefs.all[key]?.let { PrefsReader.toDto(key, it) }
                if (previous == null) {
                    call.notFound("$name has no key called $key.")
                    return@delete
                }

                val removed = inspectIo { prefs.edit().remove(key).commit() }
                if (!removed) {
                    call.badRequest("The device refused the write.")
                    return@delete
                }
                announce(name, key)
                call.respond(PrefWriteResponse(previous = previous, current = null))
            }

            delete {
                if (!call.allowsWrites(ctx.config)) return@delete
                val name = call.parameters["file"] ?: return@delete call.badRequest("Name a preferences file.")
                val prefs = open(name)
                if (prefs == null) {
                    call.notFound("No preferences file called $name.")
                    return@delete
                }

                // Clearing an encrypted file would destroy the keysets with it, and the app
                // could never read its own data again.
                if (PrefsReader.looksEncrypted(prefs.all)) {
                    call.badRequest("$name is encrypted. Clearing it would destroy its keys.")
                    return@delete
                }

                val cleared = inspectIo { prefs.edit().clear().commit() }
                if (!cleared) {
                    call.badRequest("The device refused the write.")
                    return@delete
                }
                announce(name, null)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    // ---------- Reading ----------

    private fun prefsDir(): File? {
        val dir = File(ctx.androidContext.applicationInfo.dataDir, "shared_prefs")
        return if (dir.isDirectory) dir else null
    }

    private fun listFiles(): List<PrefsFileDto> {
        val dir = prefsDir() ?: return emptyList()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".xml") } ?: return emptyList()

        return files
            .sortedBy { it.name.lowercase() }
            .map { file ->
                val name = file.name.removeSuffix(".xml")
                val registered = InspectorRegistry.prefs(name) != null
                val all = runCatching { open(name)?.all }.getOrNull()
                val encrypted = all != null && PrefsReader.looksEncrypted(all)
                // A registered instance is the app's own wrapper, so it decrypts as it reads.
                val readable = all != null && (!encrypted || registered)

                PrefsFileDto(
                    name = name,
                    keyCount = if (all == null) 0 else PrefsReader.visibleCount(all),
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    readable = readable,
                    encrypted = encrypted,
                    registered = registered,
                    note = when {
                        all == null -> "Aperture could not read this file."
                        encrypted && !registered -> ENCRYPTED_NOTE
                        else -> null
                    }
                )
            }
    }

    private fun readFile(name: String): PrefsDetailResponse? {
        val file = File(prefsDir() ?: return null, "$name.xml")
        if (!file.isFile) return null

        val registered = InspectorRegistry.prefs(name) != null
        val all = runCatching { open(name)?.all }.getOrNull()
            ?: return PrefsDetailResponse(name, false, false, registered, "Aperture could not read this file.", emptyList())

        val encrypted = PrefsReader.looksEncrypted(all)
        if (encrypted && !registered) {
            return PrefsDetailResponse(name, false, true, false, ENCRYPTED_NOTE, emptyList())
        }

        return PrefsDetailResponse(
            name = name,
            readable = true,
            encrypted = encrypted,
            registered = registered,
            note = null,
            entries = PrefsReader.entries(all)
        )
    }

    /**
     * The live instance for a file.
     *
     * A registered wrapper wins, because it decrypts. Otherwise the process-wide cache hands
     * back whatever the app itself is using. Opened on demand, never all at once: every file
     * opened here keeps its map in memory for the life of the process.
     */
    private fun open(name: String): SharedPreferences? = try {
        InspectorRegistry.prefs(name)
            ?: ctx.androidContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        Log.w(tag, "Cannot open preferences file $name", e)
        null
    }

    private fun watch(name: String) {
        synchronized(listeners) {
            if (listeners.containsKey(name)) return
            val prefs = open(name) ?: return
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                announce(name, key)
            }
            listeners[name] = listener
            runCatching { prefs.registerOnSharedPreferenceChangeListener(listener) }
        }
    }

    private fun announce(file: String, key: String?) {
        val payload = buildString {
            append("{\"file\":")
            append(ApertureBus.json.encodeToString(String.serializer(), file))
            append(",\"key\":")
            if (key == null) append("null") else append(ApertureBus.json.encodeToString(String.serializer(), key))
            append("}")
        }
        ctx.bus.emit(BusEvent(CHANNEL, "prefs_changed", payload))
    }

    companion object {
        const val CHANNEL = "prefs"
        private const val ENCRYPTED_NOTE =
            "Encrypted. Call Aperture.registerSharedPreferences(name, prefs) with the app's " +
                "EncryptedSharedPreferences to read it as plain text."
    }
}
