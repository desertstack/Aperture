package io.aperture.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.aperture.Aperture
import io.aperture.server.ApertureServer
import kotlinx.coroutines.*
import kotlin.concurrent.fixedRateTimer

/**
 * Foreground service that hosts the Ktor web server
 * Ensures the server stays alive even when app is in background
 */
class ApertureService : Service() {

    private var server: ApertureServer? = null
    private var notificationUpdateTimer: java.util.Timer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        if (intent?.action == ACTION_STOP_SERVER) {
            stopApertureServer()
            return START_NOT_STICKY
        }

        // Android gives a service started with startForegroundService() 5 seconds to promote
        // itself, and refuses the promotion if the process went to the background in between.
        if (!promoteToForeground()) {
            // The service hosts the notification, not the server, so keep the server up in the
            // process and let the service go. The host app must not crash over a notification.
            if (Aperture.getServerInstance() != null) {
                Aperture.startServerDirectly()
            }
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_CLEAR_DATA -> clearAllData()
            else -> startApertureServer()
        }

        // START_STICKY would have the system restart this service after a background kill.
        // That restart runs Application.onCreate() again in the background, which is the one
        // state where Android refuses the foreground start. Aperture restarts the service
        // itself when the app becomes visible again.
        return START_NOT_STICKY
    }

    /**
     * Show the notification and become a foreground service.
     *
     * @return false if Android refused the promotion (API 31+ background restriction).
     */
    private fun promoteToForeground(): Boolean {
        return try {
            startForeground(NOTIFICATION_ID, createNotification())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot promote to a foreground service: ${e.message}")
            false
        }
    }

    /**
     * Android 15+ withdraws the daily budget of a dataSync foreground service after six hours,
     * and gives the app a few seconds to stop it. An app that does not stop it gets an ANR.
     *
     * Give up the notification and leave the server running in the process, the same way a
     * refused foreground start does.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Android withdrew the foreground service budget, keeping the server in-process")

        // Clear the field first: onDestroy stops the server only when the service still owns it.
        server = null
        notificationUpdateTimer?.cancel()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopApertureServer()
        notificationUpdateTimer?.cancel()
        scope.cancel()
    }

    private fun startApertureServer() {
        if (server != null) {
            Log.d(TAG, "Server already running")
            return
        }

        val instance = Aperture.getServerInstance()
        if (instance == null) {
            Log.w(TAG, "Aperture is not initialized")
            stopSelf()
            return
        }

        server = instance

        // Aperture starts the server on the IO dispatcher. onStartCommand runs on the main
        // thread, and Ktor blocks its caller until the socket is bound.
        Aperture.startServerDirectly(onFailure = { stopSelf() })

        // Start periodic notification updates (every 5 seconds)
        startNotificationUpdates()

        Log.i(TAG, "Aperture server starting in foreground service")
    }

    private fun startNotificationUpdates() {
        notificationUpdateTimer?.cancel()
        notificationUpdateTimer = fixedRateTimer(
            name = "aperture-notification-updater",
            initialDelay = 5000L,
            period = 5000L
        ) {
            // The timer runs on its own thread, which is where the database read belongs.
            Aperture.refreshNotificationData()
            updateNotification()
        }
    }

    private fun stopApertureServer() {
        if (server != null) {
            // Aperture owns the server and stops it off the main thread.
            Aperture.stopServerDirectly()
            server = null
        }

        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun clearAllData() {
        Aperture.clearAllData()
        updateNotification()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aperture Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Aperture network inspector server is running"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Both values are cached. onStartCommand builds this notification on the main thread,
        // which is no place for a database query or an interface lookup.
        val serverUrl = Aperture.getServerUrl()
        val transactionCount = Aperture.getTransactionCountSnapshot()

        // Intent to open browser with server URL
        val openBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openBrowserPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openBrowserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop server
        val stopIntent = Intent(this, ApertureService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to clear data
        val clearIntent = Intent(this, ApertureService::class.java).apply {
            action = ACTION_CLEAR_DATA
        }
        val clearPendingIntent = PendingIntent.getService(
            this,
            2,
            clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        // Note: Using android system icons since custom R class isn't generated in library modules
        val iconResId = android.R.drawable.ic_dialog_info

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentTitle("Aperture Network Inspector")
            .setContentText("$transactionCount requests • Tap to open")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "📡 $transactionCount requests captured\n" +
                        "\n🌐 Network: $serverUrl\n" +
                        "🔌 ADB: ${Aperture.getLocalhostUrl()}\n" +
                        "\n💻 Port forward: ${Aperture.getAdbForwardCommand()}\n" +
                        "\nTap to open in browser"
                    )
            )
            .setContentIntent(openBrowserPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Clear",
                clearPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "ApertureService"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "aperture_server"

        const val ACTION_START_SERVER = "io.aperture.action.START_SERVER"
        const val ACTION_STOP_SERVER = "io.aperture.action.STOP_SERVER"
        const val ACTION_CLEAR_DATA = "io.aperture.action.CLEAR_DATA"

        /**
         * Start the Aperture service.
         *
         * Android 12+ refuses a foreground service start while the app is in the background and
         * throws ForegroundServiceStartNotAllowedException. Host apps call Aperture.initialize()
         * from Application.onCreate(), which also runs when the process starts for a push, a job,
         * a widget or a service restart, so the refusal is expected. It must never reach the host.
         *
         * @return true if the platform accepted the start request.
         */
        fun start(context: Context): Boolean {
            val intent = Intent(context, ApertureService::class.java).apply {
                action = ACTION_START_SERVER
            }

            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start the service from the background: ${e.message}")
                false
            }
        }

        /**
         * Stop the Aperture service.
         *
         * Android 8+ also refuses plain service starts from the background, so this reports the
         * refusal in the log instead of throwing at the caller.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ApertureService::class.java).apply {
                action = ACTION_STOP_SERVER
            }

            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot stop the service from the background: ${e.message}")
            }
        }
    }
}
