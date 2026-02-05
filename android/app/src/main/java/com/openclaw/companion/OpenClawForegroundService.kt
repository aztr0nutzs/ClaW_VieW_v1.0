package com.openclaw.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OpenClawForegroundService : Service() {
  private var gatewayClient: GatewayClient? = null

  override fun onCreate() {
    super.onCreate()
    Log.i("OPENCLAW_SERVICE", "SERVICE_START")

    val nodeId = NodeIdentity.getOrCreate(this)
    updateState { it.copy(nodeId = nodeId) }
    running.set(true)
    Log.i("OPENCLAW_SERVICE", "NODE_ID $nodeId")

    ensureChannel()
    startForeground(NOTIF_ID, buildNotif("OpenClaw Companion running"))
    Log.i("OPENCLAW_SERVICE", "START_FOREGROUND notificationId=$NOTIF_ID")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    drainQueue()
    return START_STICKY
  }

  override fun onDestroy() {
    Log.i("OPENCLAW_SERVICE", "SERVICE_STOP")
    running.set(false)
    gatewayClient?.shutdown()

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun drainQueue() {
    while (true) {
      val cmd = queue.poll() ?: break
      when (cmd) {
        is UiCommand.Connect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL connectGateway")

          val controllerUrl = cmd.controllerUrl ?: stateRef.get().controllerUrl ?: DEFAULT_CONTROLLER_URL
          if (controllerUrl.isBlank()) {
            Log.w("OPENCLAW_SERVICE", "SERVICE_ERROR missing controllerUrl")
            updateState { it.copy(lastError = "MISSING_CONTROLLER_URL") }
            return
          }
          updateState { it.copy(controllerUrl = controllerUrl, lastError = null) }
          ensureGatewayClient().connect(controllerUrl)
        }
        is UiCommand.Disconnect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL disconnectGateway")

          gatewayClient?.disconnect()
          updateState { it.copy(connected = false, registered = false, sessionId = null) }
        }
        is UiCommand.Camsnap -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL triggerCamsnap quality=${cmd.quality} maxBytes=${cmd.maxBytes}")

          // TODO: run CameraX capture and send via gateway
        }
      }
      val state = stateRef.get()
      Log.i("OPENCLAW_SERVICE", "STATE connected=${state.connected} registered=${state.registered} lastError=${state.lastError}")
    }
  }

  private fun ensureGatewayClient(): GatewayClient {
    if (gatewayClient == null) {
      gatewayClient = GatewayClient(
        onState = { connected, registered, sessionId, lastError ->
          updateState {
            it.copy(
              connected = connected,
              registered = registered,
              sessionId = sessionId,
              lastError = lastError,
            )
          }
        },
        onLog = { message ->
          Log.i("OPENCLAW_GATEWAY", message)
          appendLog(message)
        },
        getNodeId = { stateRef.get().nodeId ?: "unknown" },
      )
    }
    return gatewayClient!!
  }

  private fun updateState(transform: (UiState) -> UiState) {
    stateRef.set(transform(stateRef.get()))
  }

  private fun appendLog(message: String) {
    lastLogs.updateAndGet { existing ->
      val next = if (existing.isBlank()) message else "$existing\n$message"
      if (next.length > MAX_LOG_CHARS) {
        next.takeLast(MAX_LOG_CHARS)
      } else {
        next
      }
    }
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(CHANNEL_ID, "OpenClaw Companion", NotificationManager.IMPORTANCE_LOW)
      mgr.createNotificationChannel(ch)
    }
  }

  private fun buildNotif(text: String): Notification {
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      Notification.Builder(this)
    }
    return builder
      .setContentTitle("OpenClaw Companion")
      .setContentText(text)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setOngoing(true)
      .build()
  }

  companion object {
    private const val CHANNEL_ID = "openclaw_companion"
    private const val NOTIF_ID = 8787
    private const val DEFAULT_CONTROLLER_URL = "ws://127.0.0.1:18789"
    private const val MAX_LOG_CHARS = 8000

    private val queue = ConcurrentLinkedQueue<UiCommand>()
    private val stateRef = AtomicReference(UiState())
    private val lastLogs = AtomicReference("")
    private val running = AtomicBoolean(false)

    fun intentStart(ctx: Context): Intent = Intent(ctx, OpenClawForegroundService::class.java)

    fun enqueue(ctx: Context, cmd: UiCommand): Boolean {
      queue.add(cmd)
      val i = intentStart(ctx)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
      return true
    }

    fun getCachedState(): UiState {
      val state = stateRef.get()
      return if (running.get()) {
        state
      } else {
        state.copy(connected = false, registered = false, sessionId = null)
      }
    }

    fun getLastLogs(): String = lastLogs.get()
  }
}
