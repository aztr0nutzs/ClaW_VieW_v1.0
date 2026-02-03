package com.openclaw.clawview.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openclaw.clawview.R
import com.openclaw.clawview.camera.CameraManager
import com.openclaw.clawview.network.WebSocketGateway
import com.openclaw.clawview.storage.NodeIdentityStorage
import kotlinx.coroutines.*

private const val TAG = "NodeForegroundService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "node_service_channel"

/**
 * Real ForegroundService implementation for running the node.
 * This service:
 * - Runs in the foreground with a persistent notification
 * - Maintains WebSocket connection with heartbeat
 * - Manages camera access when needed
 * - Holds wake lock to prevent service termination
 * 
 * NEVER simulates success - all operations are real.
 */
class NodeForegroundService : Service(), WebSocketGateway.WebSocketListener {

    private lateinit var nodeStorage: NodeIdentityStorage
    private var webSocketGateway: WebSocketGateway? = null
    private var cameraManager: CameraManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nodeId: String? = null
    private var serverUrl: String? = null

    companion object {
        const val ACTION_START = "com.openclaw.clawview.action.START"
        const val ACTION_STOP = "com.openclaw.clawview.action.STOP"
        const val EXTRA_SERVER_URL = "server_url"
        
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NodeForegroundService created")
        
        nodeStorage = NodeIdentityStorage(applicationContext)
        cameraManager = CameraManager(applicationContext)
        
        // Acquire wake lock to keep service alive
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
                if (serverUrl.isNullOrBlank()) {
                    Log.e(TAG, "FAILED to start: Server URL is required but was not provided")
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                startForegroundService()
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
            else -> {
                Log.w(TAG, "Unknown action received: ${intent?.action}")
            }
        }
        
        return START_STICKY
    }

    /**
     * Start the foreground service with notification.
     */
    private fun startForegroundService() {
        Log.i(TAG, "Starting foreground service")
        
        // Create notification channel (required for Android O+)
        createNotificationChannel()
        
        // Start foreground with notification
        val notification = createNotification("Starting node service...")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        
        // Initialize node operations
        serviceScope.launch {
            try {
                initializeNode()
            } catch (e: Exception) {
                Log.e(TAG, "FAILED to initialize node: ${e.message}", e)
                stopSelf()
            }
        }
    }

    /**
     * Initialize node - get/create node ID and connect to server.
     */
    private suspend fun initializeNode() {
        // Get or create node ID
        nodeId = try {
            nodeStorage.getOrCreateNodeId()
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to get node ID: ${e.message}", e)
            return
        }
        
        Log.i(TAG, "Node initialized with ID: $nodeId")
        updateNotification("Node ID: $nodeId, Connecting...")
        
        // Connect to WebSocket server
        val url = serverUrl
        if (url.isNullOrBlank()) {
            Log.e(TAG, "FAILED: Server URL is required")
            return
        }
        
        connectToServer(url)
    }

    /**
     * Connect to WebSocket server.
     */
    private fun connectToServer(url: String) {
        val id = nodeId ?: run {
            Log.e(TAG, "FAILED to connect: Node ID not available")
            return
        }
        
        Log.i(TAG, "Connecting to server: $url with nodeId: $id")
        
        webSocketGateway = WebSocketGateway(id, this)
        webSocketGateway?.connect(url)
    }

    /**
     * Stop the foreground service.
     */
    private fun stopForegroundService() {
        Log.i(TAG, "Stopping foreground service")
        
        isRunning = false
        
        // Disconnect WebSocket
        webSocketGateway?.cleanup()
        webSocketGateway = null
        
        // Stop camera
        cameraManager?.cleanup()
        
        // Release wake lock
        releaseWakeLock()
        
        // Cancel all coroutines
        serviceScope.cancel()
        
        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        stopSelf()
    }

    /**
     * Create notification channel (required for Android O+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Create notification for foreground service.
     */
    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, Class.forName("com.openclaw.clawview.MainActivity"))
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update the notification text.
     */
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated: $contentText")
    }

    /**
     * Acquire wake lock to keep CPU awake.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ClawView::NodeServiceWakeLock"
            )
            wakeLock?.acquire()
            Log.i(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to acquire wake lock: ${e.message}", e)
        }
    }

    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to release wake lock: ${e.message}", e)
        }
    }

    // WebSocket listener implementations
    override fun onConnected() {
        Log.i(TAG, "WebSocket connected")
        updateNotification("Connected to server")
    }

    override fun onDisconnected(reason: String) {
        Log.w(TAG, "WebSocket disconnected: $reason")
        updateNotification("Disconnected: $reason")
    }

    override fun onMessage(message: String) {
        Log.d(TAG, "Received message: $message")
        // Handle messages from server
    }

    override fun onError(error: String) {
        Log.e(TAG, "WebSocket error: $error")
        updateNotification("Error: $error")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service doesn't support binding
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "NodeForegroundService destroyed")
        isRunning = false
        webSocketGateway?.cleanup()
        cameraManager?.cleanup()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
