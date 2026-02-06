package com.openclaw.companion

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Client for managing the WebSocket connection to the OpenClaw controller.
 *
 * This implementation resolves several lifecycle and thread-safety issues identified
 * during review. It introduces an intentional disconnect flag to prevent unintentional
 * reconnection after an explicit disconnect, tracks scheduled reconnect tasks so they
 * can be cancelled, synchronizes state mutations on a single lock, and exposes a
 * shutdown method to release scheduler and OkHttp resources. Register acknowledgements
 * now only influence the registered state; the connected state is derived from the
 * underlying WebSocket’s openness.
 */
class GatewayClient(
  private val onState: (connected: Boolean, registered: Boolean, lastError: String?) -> Unit,
  private val onLog: (String) -> Unit,
  private val onProtocolLog: (String) -> Unit = {},
  private val onHeartbeat: (String) -> Unit = {},
  private val onCapabilityRequest: (requestId: String?, capability: String, args: JSONObject) -> Unit = { _, _, _ -> },
  private val getNodeId: () -> String,
  private val getCapabilities: () -> JSONArray = { JSONArray() },
  private val getPlatform: () -> String = { "android" },
  private val getAppVersion: () -> String? = { null },
  private val getDeviceInfo: () -> JSONObject = { JSONObject() },
) {
  private val okHttp = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private val lock = Any()
  private var socket: WebSocket? = null
  private var reconnectAttempts = 0
  private var lastUrl: String? = null
  private var intentionalDisconnect = false
  private val scheduledReconnects = mutableListOf<ScheduledFuture<*>>()
  private var heartbeatFuture: ScheduledFuture<*>? = null
  private var lastActivityAtMillis = 0L
  private var sessionId: String? = null

  /**
   * Establish a WebSocket connection to the given URL. If a socket is already open it
   * will be closed and replaced. All mutable state is updated under [lock] and the
   * previous socket is closed outside the synchronized block to avoid reentrancy.
   */
  fun connect(url: String) {
    val socketToClose: WebSocket?
    synchronized(lock) {
      // reset intent and lastUrl before connecting to avoid stale reconnect attempts
      intentionalDisconnect = false
      lastUrl = url
      sessionId = null
      cancelScheduledReconnectsLocked()
      cancelHeartbeatLocked()
      socketToClose = socket
      socket = null
    }
    // close the previous socket outside the synchronized block to avoid callback reentrancy issues
    socketToClose?.close(1000, "reconnect")
    val request = Request.Builder().url(url).build()
    onLog("CONNECT url=$url")
    val newSocket = okHttp.newWebSocket(request, listener)
    synchronized(lock) {
      socket = newSocket
    }
  }

  /**
   * Explicitly disconnect from the gateway. This marks the client as intentionally
   * disconnected, cancels any scheduled reconnections, closes the socket, and resets
   * reconnection state. A final [onState] callback marks the client disconnected and
   * unregistered.
   */
  fun disconnect() {
    onLog("DISCONNECT")
    val socketToClose: WebSocket?
    synchronized(lock) {
      // mark as intentionally disconnected so future reconnects are suppressed
      intentionalDisconnect = true
      lastUrl = null
      reconnectAttempts = 0
      sessionId = null
      cancelScheduledReconnectsLocked()
      cancelHeartbeatLocked()
      socketToClose = socket
      socket = null
    }
    socketToClose?.close(1000, "disconnect")
    // notify that the client is fully disconnected and not registered
    onState(false, false, null)
  }

  /**
   * Completely shut down the gateway client. This cancels any scheduled reconnects,
   * closes the socket if it is open, and shuts down the scheduler and OkHttp resources.
   * This should be called when the owning service is destroyed.
   */
  fun shutdown() {
    onLog("SHUTDOWN")
    val socketToClose: WebSocket?
    synchronized(lock) {
      intentionalDisconnect = true
      lastUrl = null
      reconnectAttempts = 0
      sessionId = null
      cancelScheduledReconnectsLocked()
      cancelHeartbeatLocked()
      socketToClose = socket
      socket = null
    }
    socketToClose?.close(1000, "shutdown")
    // terminate scheduled tasks and networking resources
    scheduler.shutdownNow()
    okHttp.dispatcher.executorService.shutdown()
    okHttp.connectionPool.evictAll()
  }

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      // ignore stale sockets
      val isCurrent = synchronized(lock) { webSocket == socket }
      if (!isCurrent) {
        webSocket.close(1000, "stale")
        return
      }
      synchronized(lock) {
        reconnectAttempts = 0
        lastActivityAtMillis = System.currentTimeMillis()
      }
      onState(true, false, null)
      sendRegistration(webSocket)
      startHeartbeat()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      synchronized(lock) {
        lastActivityAtMillis = System.currentTimeMillis()
      }
      handleIncomingMessage(text, webSocket)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      val isCurrent = synchronized(lock) {
        if (webSocket != socket) {
          false
        } else {
          socket = null
          cancelHeartbeatLocked()
          true
        }
      }
      if (!isCurrent) {
        return
      }
      onLog("FAILURE ${t.message}")
      onState(false, false, "CONTROLLER_UNREACHABLE")
      scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      val isCurrent = synchronized(lock) {
        if (webSocket != socket) {
          false
        } else {
          socket = null
          cancelHeartbeatLocked()
          true
        }
      }
      if (!isCurrent) {
        return
      }
      onLog("CLOSED code=$code reason=$reason")
      onState(false, false, null)
      scheduleReconnect()
    }
  }

  private fun sendRegistration(webSocket: WebSocket) {
    val nodeId = getNodeId()
    val payload = JSONObject()
      .put("type", "register")
      .put("nodeId", nodeId)
      .put("ts", System.currentTimeMillis())
      .put("v", PROTOCOL_VERSION)
      .put("platform", getPlatform())
      .put("appVersion", getAppVersion())
      .put("capabilities", getCapabilities())
      .put("device", getDeviceInfo())
    onLog("TX ${payload}")
    onProtocolLog("TX ${payload}")
    webSocket.send(payload.toString())
  }

  /**
   * Schedule a reconnect attempt using exponential backoff. If the client was intentionally
   * disconnected or the scheduler has been shut down, no reconnection will be scheduled.
   * The scheduled future is tracked so it can be cancelled if a disconnect or shutdown occurs
   * before the attempt executes.
   */
  private fun scheduleReconnect() {
    val url: String
    val attempt: Int
    val backoffSeconds: Int
    synchronized(lock) {
      if (intentionalDisconnect || scheduler.isShutdown) {
        return
      }
      url = lastUrl ?: return
      reconnectAttempts += 1
      attempt = reconnectAttempts
      backoffSeconds = min(60, 1 shl min(reconnectAttempts, 6))
    }
    onLog("RECONNECT_ATTEMPT attempt=$attempt delay=${backoffSeconds}s")
    val futureRef = AtomicReference<ScheduledFuture<*>>()
    val task = Runnable {
      val shouldConnect: Boolean
      synchronized(lock) {
        scheduledReconnects.remove(futureRef.get())
        // only reconnect if not intentionally disconnected and the URL hasn't changed
        shouldConnect = !intentionalDisconnect && lastUrl == url
      }
      if (shouldConnect) {
        connect(url)
      }
    }
    val future = scheduler.schedule(task, backoffSeconds.toLong(), TimeUnit.SECONDS)
    futureRef.set(future)
    synchronized(lock) {
      scheduledReconnects.add(future)
    }
  }

  /**
   * Cancel all scheduled reconnect tasks. Must be called with [lock] held.
   */
  private fun cancelScheduledReconnectsLocked() {
    scheduledReconnects.forEach { it.cancel(false) }
    scheduledReconnects.clear()
  }

  /**
   * Handle an incoming message. For register acknowledgements, ensure that the callback
   * corresponds to the current socket and update connected/registered state accordingly.
   * Only the 'ok' flag determines the registered state; the connection state should reflect
   * whether the WebSocket is still open.
   */
  internal fun handleIncomingMessage(text: String, webSocket: WebSocket) {
    onLog("RX $text")
    onProtocolLog("RX $text")
    val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
    val type = msg.optString("type", "")
    if (type == "heartbeat" || type == "heartbeat_ack") {
      val ts = msg.optString("ts", "")
      val heartbeat = if (ts.isNotBlank()) ts else java.time.Instant.now().toString()
      onHeartbeat(heartbeat)
    }
    if (type == "capability_request") {
      val requestId = msg.optString("requestId", null)
      val capability = msg.optString("capability", "")
      val args = msg.optJSONObject("args") ?: JSONObject()
      if (capability.isNotBlank()) {
        onCapabilityRequest(requestId, capability, args)
      }
    }
    if (type == "register_ack") {
      val ok = msg.optBoolean("ok", false)
      val session = msg.optString("sessionId", "")
      val accepted = ok && session.isNotBlank()
      if (accepted) {
        synchronized(lock) {
          sessionId = session
        }
      }
      val isCurrent = synchronized(lock) { webSocket == socket }
      if (!isCurrent) {
        return
      }
      // connected is true if this webSocket is the active one and not null
      val connected = synchronized(lock) { socket == webSocket && socket != null }
      onState(connected, accepted, if (accepted) null else "REGISTER_ACK_FAILED")
      onLog("REGISTER_ACK ok=$accepted sessionId=${if (accepted) session else "missing"}")
    }
  }

  /**
   * Exposed for tests to inject a fake socket. Not thread-safe by itself; tests should call
   * this before any callbacks are invoked to avoid races.
   */
  internal fun setSocketForTest(webSocket: WebSocket?) {
    synchronized(lock) {
      socket = webSocket
      lastActivityAtMillis = System.currentTimeMillis()
    }
  }

  private fun startHeartbeat() {
    val future = scheduler.scheduleAtFixedRate(
      {
        val currentSocket: WebSocket?
        val lastActivity: Long
        synchronized(lock) {
          currentSocket = socket
          lastActivity = lastActivityAtMillis
        }
        if (currentSocket == null) {
          return@scheduleAtFixedRate
        }
        val now = java.time.Instant.now()
        if (now.toEpochMilli() - lastActivity > HEARTBEAT_TIMEOUT_MS) {
          onLog("HEARTBEAT_TIMEOUT lastActivityMs=${now.toEpochMilli() - lastActivity}")
          currentSocket.close(1001, "heartbeat_timeout")
          return@scheduleAtFixedRate
        }
        val heartbeat = now.toString()
        val payload = JSONObject()
          .put("type", "heartbeat")
          .put("nodeId", getNodeId())
          .put("ts", heartbeat)
          .put("v", PROTOCOL_VERSION)
        val session = synchronized(lock) { sessionId }
        if (!session.isNullOrBlank()) {
          payload.put("sessionId", session)
        }
        onLog("TX ${payload}")
        onProtocolLog("TX ${payload}")
        currentSocket.send(payload.toString())
        onHeartbeat(heartbeat)
      },
      HEARTBEAT_INTERVAL_SEC.toLong(),
      HEARTBEAT_INTERVAL_SEC.toLong(),
      TimeUnit.SECONDS,
    )
    synchronized(lock) {
      cancelHeartbeatLocked()
      heartbeatFuture = future
    }
  }

  private fun cancelHeartbeatLocked() {
    heartbeatFuture?.cancel(false)
    heartbeatFuture = null
  }

  private companion object {
    private const val HEARTBEAT_INTERVAL_SEC = 15
    private const val HEARTBEAT_TIMEOUT_MS = 45_000
    private const val PROTOCOL_VERSION = 1
  }

  fun sendCapabilityResult(result: CapabilityResult): Boolean {
    val socketRef: WebSocket?
    synchronized(lock) {
      socketRef = socket
    }
    if (socketRef == null) {
      return false
    }
    val payload = JSONObject()
      .put("type", "capability_result")
      .put("nodeId", getNodeId())
      .put("ts", System.currentTimeMillis())
      .put("v", PROTOCOL_VERSION)
      .put("requestId", result.requestId)
      .put("capability", result.capability)
      .put("ok", result.ok)
    if (result.ok) {
      if (result.data != null) payload.put("result", result.data)
    } else {
      val errorJson = JSONObject().put("code", result.code).put("message", result.message)
      result.data?.let { errorJson.put("data", it) }
      payload.put("error", errorJson)
    }
    // Log a compact summary to avoid huge log lines (e.g., when result.data contains jpegBase64).
    val summaryBuilder = StringBuilder()
      .append("TX capability_result")
      .append(" requestId=").append(result.requestId)
      .append(" capability=").append(result.capability)
      .append(" ok=").append(result.ok)
    val data = result.data
    if (result.ok && data is JSONObject && data.has("jpegBase64")) {
      val jpegBase64 = data.optString("jpegBase64", null)
      if (jpegBase64 != null) {
        summaryBuilder.append(" jpegBase64Length=").append(jpegBase64.length)
      }
    }
    onLog(summaryBuilder.toString())
    onProtocolLog(summaryBuilder.toString())
    return socketRef.send(payload.toString())
  }
}
