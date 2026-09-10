package io.aperture

/**
 * Configuration class for Aperture (No-Op version)
 * This mirrors the main library's config for API compatibility
 */
data class ApertureConfig(
    val enabled: Boolean = false,
    val port: Int = 8080,
    val autoStart: Boolean = false,
    val maxRecords: Int = 1000,
    val retentionDays: Int = 7,
    val maxBodySize: Long = 5 * 1024 * 1024,
    val requireAuth: Boolean = false,
    val customToken: String? = null,
    val showNotification: Boolean = false,
    val localhostOnly: Boolean = false,
    val alwaysReadResponseBody: Boolean = false,
    val headersToRedact: Set<String> = emptySet(),
    val allowWrites: Boolean = false,
    val inspectors: Set<ApertureInspector> = ApertureInspector.ALL
) {
    companion object {
        val DEFAULT = ApertureConfig()
        val MINIMAL = ApertureConfig()
        val LOCALHOST_ONLY = ApertureConfig()
        val NETWORK_ONLY = ApertureConfig()
    }
}
