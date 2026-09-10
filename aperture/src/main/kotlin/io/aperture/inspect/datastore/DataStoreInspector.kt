package io.aperture.inspect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * Preferences DataStore.
 *
 * Registration only, and not out of caution. `SingleProcessDataStore` keeps a process-wide set
 * of the files it has open and fails a `check()` if a second DataStore is built over one of
 * them, so Aperture opening its own would crash the host app. It also holds its values in
 * memory and never watches the file, so a write behind its back would be invisible to the
 * running app and overwritten by its next edit.
 *
 * A store the host app registered is read and written through the app's own instance, so its
 * flows carry on working. A store Aperture merely found on disk is listed by name and size,
 * with a note about registering it.
 */
internal class DataStoreInspector(
    private val ctx: InspectorContext
) : InspectorModule {

    override val id = "datastore"
    override val label = "DataStore"
    override val icon = "layers"
    override val writable = true

    override fun isAvailable(): Boolean = try {
        // The host app may not use DataStore at all, in which case the class is not here.
        Class.forName("androidx.datastore.preferences.core.PreferencesKt")
        true
    } catch (e: Throwable) {
        false
    }

    override fun register(route: Route) {
        route.get { call.respond(PrefsListResponse(inspectIo { listStores() })) }

        route.route("/{name}") {
            get {
                val name = call.parameters["name"] ?: return@get call.badRequest("Name a store.")
                val store = store(name)
                if (store == null) {
                    call.respond(unregistered(name))
                    return@get
                }
                call.respond(
                    PrefsDetailResponse(
                        name = name,
                        readable = true,
                        encrypted = false,
                        registered = true,
                        entries = inspectIo { entries(store) }
                    )
                )
            }

            put("/entry") {
                if (!call.allowsWrites(ctx.config)) return@put
                val name = call.parameters["name"] ?: return@put call.badRequest("Name a store.")
                val store = store(name)
                if (store == null) {
                    call.badRequest(REGISTER_NOTE)
                    return@put
                }

                val request = call.receive<PrefWriteRequest>()
                val previous = inspectIo { entries(store) }.firstOrNull { it.key == request.key }

                if (previous != null && previous.type != request.type && !request.allowTypeChange) {
                    call.badRequest(
                        "${request.key} is stored as ${previous.type}. Changing it to ${request.type} " +
                            "would crash the app when it next reads that key."
                    )
                    return@put
                }

                val failure = inspectIo { write(store, request) }
                if (failure != null) {
                    call.badRequest(failure)
                    return@put
                }

                announce(name, request.key)
                val current = inspectIo { entries(store) }.firstOrNull { it.key == request.key }
                call.respond(PrefWriteResponse(previous = previous, current = current))
            }

            delete("/entry") {
                if (!call.allowsWrites(ctx.config)) return@delete
                val name = call.parameters["name"] ?: return@delete call.badRequest("Name a store.")
                val key = call.request.queryParameters["key"]
                    ?: return@delete call.badRequest("Name the key to remove.")
                val store = store(name) ?: return@delete call.badRequest(REGISTER_NOTE)

                val previous = inspectIo { entries(store) }.firstOrNull { it.key == key }
                if (previous == null) {
                    call.notFound("$name has no key called $key.")
                    return@delete
                }

                inspectIo {
                    store.edit { prefs ->
                        prefs.asMap().keys.firstOrNull { it.name == key }?.let { prefs.remove(it) }
                    }
                }
                announce(name, key)
                call.respond(PrefWriteResponse(previous = previous, current = null))
            }
        }
    }

    // ---------- Reading ----------

    @Suppress("UNCHECKED_CAST")
    private fun store(name: String): DataStore<Preferences>? =
        InspectorRegistry.dataStore(name) as? DataStore<Preferences>

    private suspend fun entries(store: DataStore<Preferences>): List<PrefEntryDto> =
        store.data.first().asMap().entries
            .sortedBy { it.key.name }
            .map { (key, value) -> toDto(key.name, value) }

    private fun toDto(key: String, value: Any?): PrefEntryDto = when (value) {
        is Set<*> -> PrefEntryDto(key, "stringSet", values = value.map { it?.toString() ?: "" })
        is Boolean -> PrefEntryDto(key, "boolean", value.toString())
        is Int -> PrefEntryDto(key, "int", value.toString())
        is Long -> PrefEntryDto(key, "long", value.toString())
        is Float -> PrefEntryDto(key, "float", value.toString())
        is Double -> PrefEntryDto(key, "double", value.toString())
        else -> PrefEntryDto(key, "string", value?.toString())
    }

    /**
     * Stores Aperture found on disk but cannot reach.
     *
     * The scan is a guess: `preferencesDataStore(name = …)` puts its file here, but
     * `PreferenceDataStoreFactory.create { File(…) }` can put it anywhere. Registration is what
     * settles the matter.
     */
    private suspend fun listStores(): List<PrefsFileDto> {
        val registered = InspectorRegistry.dataStoreNames()
        val dir = File(ctx.androidContext.filesDir, "datastore")
        val onDisk = (dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) } ?: emptyArray())
            .associateBy { it.name.removeSuffix(SUFFIX) }

        val names = (registered + onDisk.keys).toSortedSet()
        return names.map { name ->
            val file = onDisk[name]
            val isRegistered = name in registered
            PrefsFileDto(
                name = name,
                // A registered store can be counted. One only seen on disk cannot be read at all.
                keyCount = if (isRegistered) runCatching { entries(store(name)!!).size }.getOrDefault(0) else 0,
                sizeBytes = file?.length() ?: 0,
                lastModified = file?.lastModified() ?: 0,
                readable = isRegistered,
                encrypted = false,
                registered = isRegistered,
                note = if (isRegistered) null else REGISTER_NOTE
            )
        }
    }

    private fun unregistered(name: String) = PrefsDetailResponse(
        name = name,
        readable = false,
        encrypted = false,
        registered = false,
        note = REGISTER_NOTE,
        entries = emptyList()
    )

    // ---------- Writing ----------

    /**
     * @return a reason the write did not happen, or null when it did.
     *
     * An existing key is written through the key object already in the store, so its type
     * cannot drift. Reading an Int key that now holds a Long throws ClassCastException inside
     * the host app.
     */
    private suspend fun write(store: DataStore<Preferences>, request: PrefWriteRequest): String? {
        val value = request.value
        var failure: String? = null

        store.edit { prefs ->
            val existing = prefs.asMap().keys.firstOrNull { it.name == request.key }

            @Suppress("UNCHECKED_CAST")
            fun put(parsed: Any?) {
                if (parsed == null) {
                    failure = "$value is not a ${request.type}."
                    return
                }
                val key = (existing ?: keyFor(request.type, request.key)) as? Preferences.Key<Any>
                if (key == null) {
                    failure = "${request.type} is not a DataStore type."
                    return
                }
                prefs[key] = parsed
            }

            when (request.type) {
                "string" -> put(value ?: "")
                "boolean" -> put(value?.trim()?.lowercase()?.let { if (it == "true" || it == "false") it.toBoolean() else null })
                "int" -> put(value?.trim()?.toIntOrNull())
                "long" -> put(value?.trim()?.toLongOrNull())
                "float" -> put(value?.trim()?.toFloatOrNull())
                "double" -> put(value?.trim()?.toDoubleOrNull())
                "stringSet" -> put(request.values?.toSet())
                else -> failure = "${request.type} is not a DataStore type."
            }
        }
        return failure
    }

    private fun keyFor(type: String, name: String): Preferences.Key<*>? = when (type) {
        "string" -> stringPreferencesKey(name)
        "boolean" -> booleanPreferencesKey(name)
        "int" -> intPreferencesKey(name)
        "long" -> longPreferencesKey(name)
        "float" -> floatPreferencesKey(name)
        "double" -> doublePreferencesKey(name)
        "stringSet" -> stringSetPreferencesKey(name)
        else -> null
    }

    private fun announce(name: String, key: String) {
        val store = ApertureBus.json.encodeToString(String.serializer(), name)
        val encodedKey = ApertureBus.json.encodeToString(String.serializer(), key)
        ctx.bus.emit(BusEvent(CHANNEL, "datastore_changed", """{"file":$store,"key":$encodedKey}"""))
    }

    companion object {
        const val CHANNEL = "datastore"
        private const val SUFFIX = ".preferences_pb"
        private const val REGISTER_NOTE =
            "Not registered. DataStore holds its values in memory, so Aperture cannot read or " +
                "write this store from the file. Call Aperture.registerDataStore(name, dataStore)."
    }
}
