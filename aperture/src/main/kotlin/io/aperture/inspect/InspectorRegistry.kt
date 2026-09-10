package io.aperture.inspect

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * What the host app has handed Aperture to look at.
 *
 * Aperture can find most storage on its own, but finding is not the same as reaching. An
 * encrypted preferences file read from disk is ciphertext; read through the app's own
 * `EncryptedSharedPreferences` wrapper it is plain text. A database written through a second
 * connection cannot fire Room's invalidation triggers, because those are TEMP objects that
 * belong to one connection; written through the app's own database, it can.
 *
 * So the host app registers what it wants Aperture to reach properly, and Aperture degrades
 * gracefully for everything else.
 */
internal object InspectorRegistry {

    private val prefs = ConcurrentHashMap<String, SharedPreferences>()
    private val dataStores = ConcurrentHashMap<String, Any>()
    private val databases = ConcurrentHashMap<String, Any>()

    /**
     * @param name the preferences file name, without `.xml`, so Aperture can match it to the
     *   file it found on disk instead of listing it twice.
     */
    fun registerPrefs(name: String, instance: SharedPreferences) {
        prefs[name.removeSuffix(".xml")] = instance
    }

    fun prefs(name: String): SharedPreferences? = prefs[name]

    fun prefNames(): Set<String> = prefs.keys.toSet()

    fun registerDataStore(name: String, instance: Any) {
        dataStores[name] = instance
    }

    fun dataStore(name: String): Any? = dataStores[name]

    fun dataStoreNames(): Set<String> = dataStores.keys.toSet()

    fun registerDatabase(name: String, instance: Any) {
        databases[name.removeSuffix(".db")] = instance
    }

    fun database(name: String): Any? = databases[name.removeSuffix(".db")]

    fun databaseNames(): Set<String> = databases.keys.toSet()

    /** Drop everything. Called when Aperture resets after a failed start. */
    fun clear() {
        prefs.clear()
        dataStores.clear()
        databases.clear()
    }
}
