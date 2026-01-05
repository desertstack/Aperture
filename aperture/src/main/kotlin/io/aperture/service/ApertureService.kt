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

        when (intent?.action) {
            ACTION_START_SERVER -> startApertureServer()
            ACTION_STOP_SERVER -> stopApertureServer()
            ACTION_CLEAR_DATA -> clearAllData()
            else -> startApertureServer()
        }

        return START_STICKY
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
            updateNotification()
            return
        }

        try {
            // Get the server instance from Aperture
            server = Aperture.getServerInstance()
            server?.start()

            // Start as foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification())

            // Start periodic notification updates (every 5 seconds)
            startNotificationUpdates()

            Log.i(TAG, "Aperture server started in foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            stopSelf()
        }
    }

    private fun startNotificationUpdates() {
        notificationUpdateTimer?.cancel()
        notificationUpdateTimer = fixedRateTimer(
            name = "aperture-notification-updater",
            initialDelay = 5000L,
            period = 5000L
        ) {
            updateNotification()
        }
    }

    private fun stopApertureServer() {
        server?.stop()
        server = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
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
        val serverUrl = Aperture.getServerUrl()
        val transactionCount = try {
            kotlinx.coroutines.runBlocking { Aperture.getTransactionCount() }
        } catch (e: Exception) {
            0
        }

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
         * Start the Aperture service
         */
        fun start(context: Context) {
            val intent = Intent(context, ApertureService::class.java).apply {
                action = ACTION_START_SERVER
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the Aperture service
         */
        fun stop(context: Context) {
            val intent = Intent(context, ApertureService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }
    }
}
