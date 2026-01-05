package io.aperture

import android.content.Context
import io.aperture.data.ApertureDatabase
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.interceptor.ApertureInterceptor
import io.aperture.server.ApertureServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import java.util.UUID

/**
 * Main entry point for Aperture library
 * Provides initialization and runtime control APIs (FR-CFG-003, FR-CFG-007)
 */
object Aperture {

    private var context: Context? = null
    private var config: ApertureConfig = ApertureConfig.DEFAULT
    private var database: ApertureDatabase? = null
    private var repository: TransactionRepository? = null
    private var server: ApertureServer? = null
    private var interceptor: ApertureInterceptor? = null
    private var authToken: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize Aperture with the given configuration
     * Should be called in Application.onCreate() (FR-CFG-003)
     *
     * @param context Application context
     * @param config Configuration options
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, config: ApertureConfig = ApertureConfig.DEFAULT) {
        if (!config.enabled) {
            android.util.Log.d("Aperture", "Aperture is disabled via config")
            return
        }

        this.context = context.applicationContext
        this.config = config

        // Initialize database
        database = ApertureDatabase.getInstance(context.applicationContext)
        repository = TransactionRepository(
            dao = database!!.transactionDao(),
            maxRecords = config.maxRecords,
            retentionDays = config.retentionDays
        )

        // Generate or use custom auth token
        authToken = config.customToken ?: UUID.randomUUID().toString()

        // Initialize interceptor
        interceptor = ApertureInterceptor(
            repository = repository!!,
            config = config
        )

        // Initialize server
        server = ApertureServer(
            context = context.applicationContext,
            repository = repository!!,
            config = config,
            authToken = if (config.requireAuth) authToken else null
        )

        // Auto-start server if configured
        if (config.autoStart) {
            startServer()
        }

        android.util.Log.d("Aperture", "Aperture initialized successfully")
    }

    /**
     * Get the OkHttp Interceptor instance
     * Add this to your OkHttp client (FR-CFG-004)
     */
    @JvmStatic
    fun getInterceptor(): Interceptor {
        return interceptor ?: throw IllegalStateException("Aperture not initialized. Call initialize() first.")
    }

    /**
     * Start the web server in a foreground service
     */
    @JvmStatic
    fun startServer() {
        val ctx = context ?: throw IllegalStateException("Aperture not initialized")

        if (config.showNotification) {
            // Start in foreground service
            io.aperture.service.ApertureService.start(ctx)
            android.util.Log.i("Aperture", "Starting server in foreground service")
        } else {
            // Start directly (not recommended for production)
            startServerDirectly()
        }
    }

    /**
     * Stop the web server
     */
    @JvmStatic
    fun stopServer() {
        val ctx = context ?: return

        if (config.showNotification) {
            // Stop foreground service
            io.aperture.service.ApertureService.stop(ctx)
        } else {
            // Stop server directly
            stopServerDirectly()
        }
    }

    /**
     * Start server directly without foreground service (internal use)
     */
    internal fun startServerDirectly() {
        val srv = server ?: throw IllegalStateException("Aperture not initialized")

        try {
            srv.start()

            // Log network access URL
            val networkUrl = getServerUrl()
            android.util.Log.i("Aperture", "═══════════════════════════════════════")
            android.util.Log.i("Aperture", "🌐 Aperture Server Started")
            android.util.Log.i("Aperture", "═══════════════════════════════════════")
            android.util.Log.i("Aperture", "📱 Same Network:  $networkUrl")
            android.util.Log.i("Aperture", "🔌 ADB Forward:   ${getLocalhostUrl()}")
            android.util.Log.i("Aperture", "")
            android.util.Log.i("Aperture", "💻 To access from computer when on cellular:")
            android.util.Log.i("Aperture", "   Run: ${getAdbForwardCommand()}")
            android.util.Log.i("Aperture", "   Open: ${getLocalhostUrl()}")

            if (config.requireAuth) {
                android.util.Log.i("Aperture", "")
                android.util.Log.i("Aperture", "🔐 Auth Token: $authToken")
            }

            android.util.Log.i("Aperture", "═══════════════════════════════════════")
        } catch (e: Exception) {
            android.util.Log.e("Aperture", "Failed to start server", e)
        }
    }

    /**
     * Stop server directly (internal use)
     */
    internal fun stopServerDirectly() {
        try {
            server?.stop()
            android.util.Log.i("Aperture", "Server stopped")
        } catch (e: Exception) {
            android.util.Log.e("Aperture", "Failed to stop server", e)
        }
    }

    /**
     * Get the server instance (internal use by service)
     */
    @JvmStatic
    internal fun getServerInstance(): ApertureServer? {
        return server
    }

    /**
     * Check if server is currently running
     */
    @JvmStatic
    fun isServerRunning(): Boolean {
        return server?.isRunning() ?: false
    }

    /**
     * Get the server URL for network access
     * Returns the full URL where the web UI can be accessed from other devices
     */
    @JvmStatic
    fun getServerUrl(): String {
        val ctx = context ?: throw IllegalStateException("Aperture not initialized")
        val host = if (config.localhostOnly) {
            "127.0.0.1"
        } else {
            getLocalIpAddress(ctx)
        }
        return "http://$host:${config.port}"
    }

    /**
     * Get the localhost URL for ADB port forwarding
     * Use this when device is on cellular data or different network
     */
    @JvmStatic
    fun getLocalhostUrl(): String {
        return "http://localhost:${config.port}"
    }

    /**
     * Get the ADB port forward command
     * Run this on your computer to access server when device is on cellular/different network
     */
    @JvmStatic
    fun getAdbForwardCommand(): String {
        return "adb forward tcp:${config.port} tcp:${config.port}"
    }

    /**
     * Get the authentication token (if auth is enabled)
     */
    @JvmStatic
    fun getAuthToken(): String? {
        return if (config.requireAuth) authToken else null
    }

    /**
     * Clear all stored transaction data
     */
    @JvmStatic
    fun clearAllData() {
        scope.launch {
            try {
                repository?.deleteAll()
                android.util.Log.d("Aperture", "All data cleared")
            } catch (e: Exception) {
                android.util.Log.e("Aperture", "Failed to clear data", e)
            }
        }
    }

    /**
     * Clear transactions older than specified days
     */
    @JvmStatic
    fun clearOldData(olderThanDays: Int) {
        scope.launch {
            try {
                repository?.clearOldData(olderThanDays)
                android.util.Log.d("Aperture", "Cleared data older than $olderThanDays days")
            } catch (e: Exception) {
                android.util.Log.e("Aperture", "Failed to clear old data", e)
            }
        }
    }

    /**
     * Get total count of stored transactions
     */
    @JvmStatic
    suspend fun getTransactionCount(): Int {
        return repository?.getCount() ?: 0
    }

    /**
     * Get a specific transaction by ID
     */
    @JvmStatic
    suspend fun getTransaction(id: Long): HttpTransaction? {
        return repository?.getById(id)
    }

    /**
     * Get the repository instance (for advanced usage)
     */
    @JvmStatic
    internal fun getRepository(): TransactionRepository? {
        return repository
    }

    /**
     * Get local IP address for network access
     */
    private fun getLocalIpAddress(context: Context): String {
        try {
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Aperture", "Failed to get local IP", e)
        }
        return "0.0.0.0"
    }
}
