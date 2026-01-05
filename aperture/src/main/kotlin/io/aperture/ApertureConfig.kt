package io.aperture

/**
 * Configuration class for Aperture library
 * (FR-CFG-006)
 */
data class ApertureConfig(
    /**
     * Master enable/disable switch
     */
    val enabled: Boolean = true,

    /**
     * Port for the web server
     */
    val port: Int = 8080,

    /**
     * Automatically start server on initialization
     */
    val autoStart: Boolean = true,

    /**
     * Maximum number of records to keep in database
     * Older records are automatically deleted when limit is exceeded
     */
    val maxRecords: Int = 1000,

    /**
     * Data retention period in days
     * Records older than this are eligible for deletion
     */
    val retentionDays: Int = 7,

    /**
     * Maximum body size to store (in bytes)
     * Bodies larger than this will be truncated
     */
    val maxBodySize: Long = 5 * 1024 * 1024, // 5 MB

    /**
     * Require authentication token for web server access
     */
    val requireAuth: Boolean = false,

    /**
     * Custom authentication token
     * If null, a random token will be generated
     */
    val customToken: String? = null,

    /**
     * Show persistent notification with server status
     */
    val showNotification: Boolean = true,

    /**
     * Bind server to localhost only (disables network access)
     */
    val localhostOnly: Boolean = false,

    /**
     * Always read full response body even if not consumed by app
     */
    val alwaysReadResponseBody: Boolean = false,

    /**
     * Headers to redact from capture (e.g., "Authorization", "Cookie")
     * These headers will be replaced with "[REDACTED]"
     */
    val headersToRedact: Set<String> = emptySet()
) {
    companion object {
        /**
         * Default configuration optimized for debug builds
         */
        val DEFAULT = ApertureConfig()

        /**
         * Minimal configuration with reduced resource usage
         */
        val MINIMAL = ApertureConfig(
            maxRecords = 100,
            retentionDays = 1,
            maxBodySize = 1 * 1024 * 1024, // 1 MB
            showNotification = false
        )

        /**
         * Localhost-only configuration for enhanced security
         */
        val LOCALHOST_ONLY = ApertureConfig(
            localhostOnly = true,
            requireAuth = true
        )
    }

    /**
     * Validate configuration
     */
    init {
        require(port in 1024..65535) { "Port must be between 1024 and 65535" }
        require(maxRecords > 0) { "maxRecords must be positive" }
        require(retentionDays > 0) { "retentionDays must be positive" }
        require(maxBodySize > 0) { "maxBodySize must be positive" }
    }
}
