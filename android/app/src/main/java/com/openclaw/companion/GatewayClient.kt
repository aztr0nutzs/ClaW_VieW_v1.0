package com.openclaw.companion

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

class GatewayClient(
  context: Context,
  private val onState: (connected: Boolean, registered: Boolean, state: RegistrationState, error: GatewayError?) -> Unit,
  private val onLog: (String) -> Unit,
  private val getNodeId: () -> String,
) {
  private val okHttp = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private var socket: WebSocket? = null
  private var reconnectAttempts = 0
  private var lastUrl: String? = null
  private var registerTimeout: ScheduledFuture<*>? = null
  private val stateMachine = RegistrationStateMachine()

  fun connect(url: String) {
    lastUrl = url
    socket?.close(1000, "reconnect")
    val request = Request.Builder().url(url).build()
    onLog("CONNECT url=$url")
    transition(RegistrationEvent.ConnectRequested)
    socket = okHttp.newWebSocket(request, listener)
  }

  fun disconnect() {
    onLog("DISCONNECT")
    socket?.close(1000, "disconnect")
    socket = null
    reconnectAttempts = 0
    transition(RegistrationEvent.DisconnectRequested)
  }

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      reconnectAttempts = 0
      transition(RegistrationEvent.SocketOpened)
      sendRegistration(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      onLog("RX $text")
      val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
      val parsed = GatewayMessage.fromJson(msg)
      if (parsed == null) {
        transition(RegistrationEvent.RegisterAckError(GatewayError("REG_BAD_SCHEMA", "Invalid message")))
        return
      }
      when (parsed) {
        is GatewayMessage.RegisterAck -> {
          registerTimeout?.cancel(false)
          if (parsed.ok) {
            transition(RegistrationEvent.RegisterAckOk)
          } else {
            val err = parsed.error ?: GatewayError("REG_SERVER_ERROR", "Registration rejected")
            transition(RegistrationEvent.RegisterAckError(err))
          }
          onLog("REGISTER_ACK ok=${parsed.ok}")
        }
        is GatewayMessage.Error -> {
          transition(RegistrationEvent.RegisterAckError(GatewayError(parsed.code, parsed.message)))
        }
        else -> Unit
      }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      onLog("FAILURE ${t.message}")
      transition(RegistrationEvent.SocketFailure(t.message ?: "WebSocket failure"))
      scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      onLog("CLOSED code=$code reason=$reason")
      transition(RegistrationEvent.SocketClosed(code, reason))
      scheduleReconnect()
    }
  }

  private fun sendRegistration(webSocket: WebSocket) {
    val payload = GatewayMessage.Register(
      nodeId = getNodeId(),
      capabilities = listOf(Capability("camsnap", 1)),
    )
    transition(RegistrationEvent.RegisterSent)
    registerTimeout?.cancel(false)
    registerTimeout = scheduler.schedule(
      { transition(RegistrationEvent.RegisterTimeout); socket?.close(4000, "register_timeout") },
      8,
      TimeUnit.SECONDS,
    )
    onLog("TX ${payload.toJson()}")
    webSocket.send(payload.toJson().toString())
  }

  private fun transition(event: RegistrationEvent) {
    val from = stateMachine.state
    val result = stateMachine.transition(event)
    onLog("STATE ${from.name} -> ${result.state.name} event=${event.javaClass.simpleName}")
    val connected = result.state != RegistrationState.DISCONNECTED
    val registered = result.state == RegistrationState.REGISTERED
    onState(connected, registered, result.state, result.error)
  }

  private fun scheduleReconnect() {
    val url = lastUrl ?: return
    reconnectAttempts += 1
    val backoffSeconds = min(60, 1 shl min(reconnectAttempts, 6))
    onLog("RECONNECT_ATTEMPT attempt=$reconnectAttempts delay=${backoffSeconds}s")
    scheduler.schedule({ connect(url) }, backoffSeconds.toLong(), TimeUnit.SECONDS)
  }
}
