package com.openclaw.companion

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

class GatewayClient(
  context: Context,
  private val onState: (connected: Boolean, registered: Boolean, lastError: String?) -> Unit,
  private val onLog: (String) -> Unit,
  private val getNodeId: () -> String,
) {
  private val appContext = context.applicationContext
  private val okHttp = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var socket: WebSocket? = null
  private var reconnectAttempts = 0
  private var lastUrl: String? = null

  fun connect(url: String) {
    lastUrl = url
    socket?.close(1000, "reconnect")
    val request = Request.Builder().url(url).build()
    onLog("CONNECT url=$url")
    socket = okHttp.newWebSocket(request, listener)
  }

  fun disconnect() {
    onLog("DISCONNECT")
    socket?.close(1000, "disconnect")
    socket = null
    reconnectAttempts = 0
    onState(false, false, null)
  }

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      reconnectAttempts = 0
      onState(true, false, null)
      sendRegistration(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      onLog("RX $text")
      val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
      val type = msg.optString("type", "")
      if (type == "register_ack") {
        val ok = msg.optBoolean("ok", false)
        onState(ok, ok, if (ok) null else "REGISTER_ACK_FAILED")
        onLog("REGISTER_ACK ok=$ok")
      }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      onLog("FAILURE ${t.message}")
      onState(false, false, "WS_FAILURE")
      scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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
    val url = lastUrl ?: return
    reconnectAttempts += 1
    val backoffSeconds = min(60, 1 shl min(reconnectAttempts, 6))
    onLog("RECONNECT_ATTEMPT attempt=$reconnectAttempts delay=${backoffSeconds}s")
    scheduler.schedule({ connect(url) }, backoffSeconds.toLong(), TimeUnit.SECONDS)
  }
}
