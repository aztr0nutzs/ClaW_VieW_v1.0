package com.openclaw.companion

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

class GatewayClient(
  private val onState: (connected: Boolean, registered: Boolean, lastError: String?) -> Unit,
  private val onLog: (String) -> Unit,
  private val getNodeId: () -> String,
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

  fun connect(url: String) {
    val socketToClose: WebSocket?
    synchronized(lock) {
      intentionalDisconnect = false
      lastUrl = url
      cancelScheduledReconnectsLocked()
      socketToClose = socket
      socket = null
    }
    socketToClose?.close(1000, "reconnect")
    val request = Request.Builder().url(url).build()
    onLog("CONNECT url=$url")
    val newSocket = okHttp.newWebSocket(request, listener)
    synchronized(lock) {
      socket = newSocket
    }
  }

  fun disconnect() {
    onLog("DISCONNECT")
    val socketToClose: WebSocket?
    synchronized(lock) {
      intentionalDisconnect = true
      lastUrl = null
      reconnectAttempts = 0
      cancelScheduledReconnectsLocked()
      socketToClose = socket
      socket = null
    }
    socketToClose?.close(1000, "disconnect")
    onState(false, false, null)
  }

  fun shutdown() {
    onLog("SHUTDOWN")
    val socketToClose: WebSocket?
    synchronized(lock) {
      intentionalDisconnect = true
      lastUrl = null
      reconnectAttempts = 0
      cancelScheduledReconnectsLocked()
      socketToClose = socket
      socket = null
    }
    socketToClose?.close(1000, "shutdown")
    scheduler.shutdownNow()
    okHttp.dispatcher.executorService.shutdown()
    okHttp.connectionPool.evictAll()
  }

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      val isCurrent = synchronized(lock) { webSocket == socket }
      if (!isCurrent) {
        webSocket.close(1000, "stale")
        return
      }
      synchronized(lock) {
        reconnectAttempts = 0
      }
      onState(true, false, null)
      sendRegistration(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      handleIncomingMessage(text, webSocket)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      val isCurrent = synchronized(lock) {
        if (webSocket != socket) {
          false
        } else {
          socket = null
          true
        }
      }
      if (!isCurrent) {
        return
      }
      onLog("FAILURE ${t.message}")
      onState(false, false, "WS_FAILURE")
      scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      val isCurrent = synchronized(lock) {
        if (webSocket != socket) {
          false
        } else {
          socket = null
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
    val payload = JSONObject()
      .put("type", "register")
      .put("nodeId", getNodeId())
      .put("capabilities", capabilityManifest())
    onLog("TX ${payload}")
    webSocket.send(payload.toString())
  }

  private fun capabilityManifest(): JSONArray {
    return JSONArray()
      .put(JSONObject().put("name", "camsnap").put("version", 1))
  }

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

  private fun cancelScheduledReconnectsLocked() {
    scheduledReconnects.forEach { it.cancel(false) }
    scheduledReconnects.clear()
  }

  internal fun handleIncomingMessage(text: String, webSocket: WebSocket) {
    onLog("RX $text")
    val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
    val type = msg.optString("type", "")
    if (type == "register_ack") {
      val ok = msg.optBoolean("ok", false)
      val isCurrent = synchronized(lock) { webSocket == socket }
      if (!isCurrent) {
        return
      }
      val connected = synchronized(lock) { socket == webSocket && socket != null }
      onState(connected, ok, if (ok) null else "REGISTER_ACK_FAILED")
      onLog("REGISTER_ACK ok=$ok")
    }
  }

  internal fun setSocketForTest(webSocket: WebSocket?) {
    synchronized(lock) {
      socket = webSocket
    }
  }
}
