package com.openclaw.clawview.network

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "WebSocketGateway"
private const val HEARTBEAT_INTERVAL_MS = 30000L // 30 seconds
private const val INITIAL_RECONNECT_DELAY_MS = 1000L // 1 second
private const val MAX_RECONNECT_DELAY_MS = 60000L // 60 seconds
private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0

/**
 * WebSocket gateway for real-time communication with the server.
 * Implements heartbeat mechanism to keep connection alive and detect failures.
 * 
 * NEVER invents server endpoints - requires explicit server URL.
 * NEVER simulates success - reports all failures.
 */
class WebSocketGateway(
    private val nodeId: String,
    private val listener: WebSocketListener? = null
) {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var serverUrl: String? = null
    private var isManualDisconnect = false
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
    
    interface WebSocketListener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onMessage(message: String)
        fun onError(error: String)
    }

    /**
     * Connect to the WebSocket server.
     * @param url The server WebSocket URL (e.g., "ws://example.com:8080/nodes")
     */
    fun connect(url: String) {
        if (url.isBlank()) {
            val error = "Server URL cannot be empty. REFUSING to connect without valid URL."
            Log.e(TAG, error)
            listener?.onError(error)
            return
        }
        
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            val error = "Invalid WebSocket URL: $url. Must start with ws:// or wss://"
            Log.e(TAG, error)
            listener?.onError(error)
            return
        }
        
        serverUrl = url
        isManualDisconnect = false
        
        Log.i(TAG, "Connecting to WebSocket: $url with nodeId: $nodeId")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("X-Node-Id", nodeId)
            .build()
        
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to $url")
                listener?.onConnected()
                // Reset reconnect delay on successful connection
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
                startHeartbeat()
                
                // Send initial registration message
                sendRegistrationMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: code=$code, reason=$reason")
                stopHeartbeat()
                listener?.onDisconnected("Connection closed: $reason")
                
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = "WebSocket connection FAILED: ${t.message}"
                Log.e(TAG, error, t)
                stopHeartbeat()
                listener?.onError(error)
                listener?.onDisconnected(error)
                
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * Disconnect from the WebSocket server.
     */
    fun disconnect() {
        Log.i(TAG, "Disconnecting WebSocket")
        isManualDisconnect = true
        stopHeartbeat()
        cancelReconnect()
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null
    }

    /**
     * Send a message through the WebSocket.
     * Returns false if not connected.
     */
    fun sendMessage(message: String): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.e(TAG, "FAILED to send message: WebSocket not connected")
            return false
        }
        
        return try {
            ws.send(message)
            Log.d(TAG, "Sent message: $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to send message: ${e.message}", e)
            false
        }
    }

    /**
     * Send registration message to identify this node.
     */
    private fun sendRegistrationMessage() {
        try {
            val registration = JSONObject().apply {
                put("type", "register")
                put("nodeId", nodeId)
                put("timestamp", System.currentTimeMillis())
            }
            
            if (!sendMessage(registration.toString())) {
                Log.e(TAG, "FAILED to send registration message")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "FAILED to create registration message", e)
        }
    }

    /**
     * Start heartbeat mechanism to keep connection alive.
     */
    private fun startHeartbeat() {
        stopHeartbeat() // Cancel any existing heartbeat
        
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeat()
            }
        }
        
        Log.d(TAG, "Heartbeat started (interval: ${HEARTBEAT_INTERVAL_MS}ms)")
    }

    /**
     * Stop heartbeat mechanism.
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped")
    }

    /**
     * Send heartbeat ping to server.
     */
    private fun sendHeartbeat() {
        try {
            val heartbeat = JSONObject().apply {
                put("type", "heartbeat")
                put("nodeId", nodeId)
                put("timestamp", System.currentTimeMillis())
            }
            
            if (!sendMessage(heartbeat.toString())) {
                Log.e(TAG, "FAILED to send heartbeat")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "FAILED to create heartbeat message", e)
        }
    }

    /**
     * Schedule automatic reconnection attempt with exponential backoff.
     */
    private fun scheduleReconnect() {
        cancelReconnect()
        
        reconnectJob = scope.launch {
            Log.i(TAG, "Scheduling reconnect in ${currentReconnectDelay}ms")
            delay(currentReconnectDelay)
            
            // Increase delay for next attempt (exponential backoff)
            currentReconnectDelay = (currentReconnectDelay * RECONNECT_BACKOFF_MULTIPLIER).toLong()
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            
            serverUrl?.let { url ->
                Log.i(TAG, "Attempting to reconnect to $url")
                connect(url)
            } ?: run {
                Log.e(TAG, "Cannot reconnect: server URL is null")
            }
        }
    }

    /**
     * Cancel scheduled reconnection.
     */
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * Handle incoming messages from server.
     */
    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "unknown")
            
            when (type) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat acknowledged by server")
                }
                "error" -> {
                    val error = json.optString("message", "Unknown error")
                    Log.e(TAG, "Server error: $error")
                    listener?.onError(error)
                }
                else -> {
                    // Forward all other messages to listener
                    listener?.onMessage(text)
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Received non-JSON message: $text")
            listener?.onMessage(text)
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up WebSocketGateway")
        disconnect()
        scope.cancel()
    }
}
