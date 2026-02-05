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

    running.set(true)
    val nodeId = NodeIdentity.getOrCreate(this)
    val storedUrl = ControllerConfig.getControllerUrl(this)
    stateRef.set(stateRef.get().copy(nodeId = nodeId, controllerUrl = storedUrl, gatewayUrl = storedUrl))
    Log.i("OPENCLAW_SERVICE", "NODE_ID $nodeId")

    ensureChannel()
    startForeground(NOTIF_ID, buildNotif("OpenClaw Companion running"))
    Log.i("OPENCLAW_SERVICE", "START_FOREGROUND notificationId=$NOTIF_ID")

  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // In real implementation, this service owns the gateway and processes commands.
    drainQueue()
    return START_STICKY
  }

  override fun onDestroy() {
    Log.i("OPENCLAW_SERVICE", "SERVICE_STOP")
    running.set(false)
    gatewayClient?.disconnect()

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun drainQueue() {
    while (true) {
      val cmd = queue.poll() ?: break
      when (cmd) {
        is UiCommand.Connect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL connectGateway")

          val controllerUrl = cmd.controllerUrl ?: stateRef.get().controllerUrl
          if (controllerUrl.isNullOrBlank()) {
            Log.w("OPENCLAW_SERVICE", "SERVICE_ERROR missing controllerUrl")
            updateState {
              it.copy(
                lastErrorCode = "WS_PROTOCOL_ERROR",
                lastErrorMessage = "Missing controller URL",
                registrationState = RegistrationState.ERROR.name,
              )
            }
            return
          }
          if (!isValidGatewayUrl(controllerUrl)) {
            Log.w("OPENCLAW_SERVICE", "SERVICE_ERROR invalid controllerUrl=$controllerUrl")
            updateState {
              it.copy(
                controllerUrl = controllerUrl,
                gatewayUrl = controllerUrl,
                lastErrorCode = "WS_PROTOCOL_ERROR",
                lastErrorMessage = "Invalid URL format",
                registrationState = RegistrationState.ERROR.name,
              )
            }
            return
          }
          ControllerConfig.setControllerUrl(this, controllerUrl)
          updateState {
            it.copy(
              controllerUrl = controllerUrl,
              gatewayUrl = controllerUrl,
              lastErrorCode = null,
              lastErrorMessage = null,
            )
          }
          ensureGatewayClient().connect(controllerUrl)
        }
        is UiCommand.Disconnect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL disconnectGateway")

          gatewayClient?.disconnect()
          updateState { it.copy(connected = false, registered = false, registrationState = RegistrationState.DISCONNECTED.name) }
        }
        is UiCommand.Camsnap -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL triggerCamsnap quality=${cmd.quality} maxBytes=${cmd.maxBytes}")

          // TODO: run CameraX capture and send via gateway
          // Until implemented, this MUST remain an explicit failure in capability execution (not here).
        }
      }
      Log.i("OPENCLAW_SERVICE", "STATE connected=${stateRef.get().connected} registered=${stateRef.get().registered} lastError=${stateRef.get().lastErrorCode}")

    }
  }

  private fun ensureGatewayClient(): GatewayClient {
    if (gatewayClient == null) {
      gatewayClient = GatewayClient(
        applicationContext,
        onState = { connected, registered, regState, error ->
          updateState {
            it.copy(
              connected = connected,
              registered = registered,
              registrationState = regState.name,
              lastErrorCode = error?.code,
              lastErrorMessage = error?.message,
            )
          }
        },
        onLog = { message -> Log.i("OPENCLAW_GATEWAY", message) },
        getNodeId = { stateRef.get().nodeId ?: "unknown" },
      )
    }
    return gatewayClient!!
  }

  private fun updateState(transform: (UiState) -> UiState) {
    stateRef.set(transform(stateRef.get()))
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

    private val queue = ConcurrentLinkedQueue<UiCommand>()
    private val stateRef = AtomicReference(UiState())
    private val lastLogs = AtomicReference("")
    private val running = AtomicBoolean(false)

    fun intentStart(ctx: Context): Intent = Intent(ctx, OpenClawForegroundService::class.java)

    fun enqueue(ctx: Context, cmd: UiCommand): Boolean {
      queue.add(cmd)
      // ensure service running
      val i = intentStart(ctx)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
      return true
    }

    fun getUiStateSnapshot(): UiState {
      val state = stateRef.get()
      return if (running.get()) {
        state
      } else {
        state.copy(
          nodeId = null,
          connected = false,
          registered = false,
          registrationState = RegistrationState.DISCONNECTED.name,
        )
      }
    }
    fun getLastLogs(): String = lastLogs.get()
  }

  private fun isValidGatewayUrl(url: String): Boolean {
    return url.startsWith("ws://") || url.startsWith("wss://")
  }
}
