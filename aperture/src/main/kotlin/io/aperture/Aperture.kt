package io.aperture

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import io.aperture.data.ApertureDatabase
import io.aperture.data.entity.HttpTransaction
import io.aperture.data.repository.TransactionRepository
import io.aperture.interceptor.ApertureInterceptor
import io.aperture.server.ApertureServer
import io.aperture.service.ApertureService
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
    private var pendingServiceStart: Application.ActivityLifecycleCallbacks? = null

    // Cached so the notification and getServerUrl() never touch the database or the network
    // interfaces on the calling thread. refreshNotificationData() updates both.
    @Volatile
    private var transactionCount: Int = 0

    @Volatile
    private var cachedIpAddress: String? = null

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

        try {
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
        } catch (e: Exception) {
            // Aperture is a debug tool. It must not take the host app down with it.
            android.util.Log.e("Aperture", "Aperture failed to initialize, the app runs without it", e)
            reset()
            return
        }

        // Auto-start server if configured
        if (config.autoStart) {
            startServer()
        }

        android.util.Log.d("Aperture", "Aperture initialized successfully")
    }

    /**
     * Drop everything a failed initialize() left behind, so the entry points fall back to
     * their do-nothing behaviour instead of using half-built objects.
     */
    private fun reset() {
        context = null
        database = null
        repository = null
        interceptor = null
        server = null
    }

    /**
     * Get the OkHttp Interceptor instance
     * Add this to your OkHttp client (FR-CFG-004)
     *
     * Returns a pass-through interceptor if Aperture is not initialized, so the host app keeps
     * its network stack either way.
     */
    @JvmStatic
    fun getInterceptor(): Interceptor {
        return interceptor ?: run {
            android.util.Log.e(
                "Aperture",
                "Aperture is not initialized. Requests pass through without capture."
            )
            PassThroughInterceptor
        }
    }

    /**
     * Hands every request to the rest of the chain and captures nothing
     */
    private object PassThroughInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
    }

    /**
     * Start the web server in a foreground service
     */
    @JvmStatic
    fun startServer() {
        val ctx = context
        if (ctx == null) {
            android.util.Log.w("Aperture", "Aperture is not initialized, cannot start the server")
            return
        }

        if (!config.showNotification) {
            // Start directly (not recommended for production)
            startServerDirectly()
            return
        }

        if (ApertureService.start(ctx)) {
            android.util.Log.i("Aperture", "Starting server in foreground service")
            return
        }

        // Android 12+ refuses a foreground service start while the app is in the background.
        // initialize() runs from Application.onCreate(), which the system also calls when the
        // process starts for a push, a job or a widget. Run the server in the process now and
        // move it into the service when the app becomes visible.
        android.util.Log.i("Aperture", "Foreground service refused, starting server in-process")
        startServerDirectly()
        startServiceWhenVisible(ctx)
    }

    /**
     * Retry the foreground service start when the host app shows an activity, which is the
     * first moment Android 12+ permits it.
     */
    private fun startServiceWhenVisible(ctx: Context) {
        val app = ctx.applicationContext as? Application ?: return
        if (pendingServiceStart != null) return

        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                cancelPendingServiceStart()
                ApertureService.start(app)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        pendingServiceStart = callbacks
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    private fun cancelPendingServiceStart() {
        val callbacks = pendingServiceStart ?: return
        pendingServiceStart = null
        (context?.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(callbacks)
    }

    /**
     * Stop the web server
     */
    @JvmStatic
    fun stopServer() {
        val ctx = context ?: return

        cancelPendingServiceStart()

        if (config.showNotification) {
            // Stop foreground service
            ApertureService.stop(ctx)
        }

        // The server also runs in the process when the service was refused, and stopping an
        // already stopped server does nothing, so always stop it here.
        stopServerDirectly()
    }

    /**
     * Start server directly without foreground service (internal use)
     *
     * Returns immediately. Ktor binds the socket and loads its plugins on the calling thread,
     * and every caller here is the main thread: Application.onCreate() through initialize(),
     * or Service.onStartCommand(). The work goes to the IO dispatcher instead.
     *
     * @param onFailure runs on a background thread if the server cannot start.
     */
    internal fun startServerDirectly(onFailure: (() -> Unit)? = null) {
        val srv = server
        if (srv == null) {
            android.util.Log.w("Aperture", "Aperture is not initialized, cannot start the server")
            onFailure?.invoke()
            return
        }

        scope.launch {
            if (srv.isRunning()) return@launch

            try {
                srv.start()
                logServerAccess()
            } catch (e: Exception) {
                android.util.Log.e("Aperture", "Failed to start server", e)
                onFailure?.invoke()
            }
        }
    }

    /**
     * Stop server directly (internal use)
     *
     * Returns immediately. Netty waits for its event loops to wind down, up to the grace
     * period, which must not happen on the main thread.
     */
    internal fun stopServerDirectly() {
        val srv = server ?: return

        scope.launch {
            try {
                srv.stop()
                android.util.Log.i("Aperture", "Server stopped")
            } catch (e: Exception) {
                android.util.Log.e("Aperture", "Failed to stop server", e)
            }
        }
    }

    /**
     * Log the addresses the web UI is available on
     */
    private fun logServerAccess() {
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
     *
     * The server starts on a background thread, so this turns true shortly after startServer().
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
        if (context == null) return ""

        val host = if (config.localhostOnly) "127.0.0.1" else getLocalIpAddress()
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
        return (repository?.getCount() ?: 0).also { transactionCount = it }
    }

    /**
     * Last known count of stored transactions
     *
     * Reads no database, so it is safe on the main thread. Use getTransactionCount() from a
     * coroutine for a fresh count.
     */
    @JvmStatic
    fun getTransactionCountSnapshot(): Int {
        return transactionCount
    }

    /**
     * Re-read the values the notification shows
     *
     * Queries the database and enumerates the network interfaces, so call it from a background
     * thread. The service calls it from its notification timer.
     */
    internal fun refreshNotificationData() {
        try {
            transactionCount = kotlinx.coroutines.runBlocking { repository?.getCount() ?: 0 }
        } catch (e: Exception) {
            android.util.Log.w("Aperture", "Cannot read the transaction count: ${e.message}")
        }

        cachedIpAddress = readLocalIpAddress()
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
     *
     * Enumerating the interfaces is a system call, and getServerUrl() is called from the main
     * thread and from every notification update, so the answer is cached. The service refreshes
     * it on its own thread through refreshNotificationData().
     */
    private fun getLocalIpAddress(): String {
        return cachedIpAddress ?: readLocalIpAddress().also { cachedIpAddress = it }
    }

    private fun readLocalIpAddress(): String {
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
