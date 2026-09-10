package io.aperture

/**
 * The domains Aperture can inspect.
 *
 * Use [ApertureConfig.inspectors] to choose which ones the web console shows. An inspector
 * that is not listed registers no routes at all, so its data never leaves the device.
 */
enum class ApertureInspector {
    /** HTTP traffic captured by the OkHttp interceptor. */
    NETWORK,

    /** Files in the app's `shared_prefs` directory. */
    PREFS,

    /** Registered Preferences DataStore instances. */
    DATASTORE,

    /** SQLite databases in the app's `databases` directory. */
    DATABASES,

    /** The app's private file sandbox. */
    FILES;

    companion object {
        /** Every inspector. This is the default. */
        @JvmField
        val ALL: Set<ApertureInspector> = values().toSet()

        /** Network traffic only, which is what Aperture did before version 1.2. */
        @JvmField
        val NETWORK_ONLY: Set<ApertureInspector> = setOf(NETWORK)

        /** Everything that reads or writes local app state. */
        @JvmField
        val STORAGE: Set<ApertureInspector> = setOf(PREFS, DATASTORE, DATABASES, FILES)
    }
}
