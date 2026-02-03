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
import java.util.concurrent.atomic.AtomicReference

class OpenClawForegroundService : Service() {

  override fun onCreate() {
    super.onCreate()
    Log.i("OPENCLAW_SERVICE", "SERVICE_START")

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

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun drainQueue() {
    while (true) {
      val cmd = queue.poll() ?: break
      when (cmd) {
        is UiCommand.Connect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL connectGateway")

          // TODO: call gateway.connect()
          stateRef.set(stateRef.get().copy(connected = true, controllerUrl = stateRef.get().controllerUrl ?: "ws://<controller>"))
        }
        is UiCommand.Disconnect -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL disconnectGateway")

          // TODO: call gateway.disconnect()
          stateRef.set(stateRef.get().copy(connected = false, registered = false))
        }
        is UiCommand.Camsnap -> {
          Log.i("OPENCLAW_SERVICE", "SERVICE_CALL triggerCamsnap quality=${cmd.quality} maxBytes=${cmd.maxBytes}")

          // TODO: run CameraX capture and send via gateway
          // Until implemented, this MUST remain an explicit failure in capability execution (not here).
        }
      }
      Log.i("OPENCLAW_SERVICE", "STATE connected=${stateRef.get().connected} registered=${stateRef.get().registered} lastError=${stateRef.get().lastError}")

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

    private val queue = ConcurrentLinkedQueue<UiCommand>()
    private val stateRef = AtomicReference(UiState())
    private val lastLogs = AtomicReference("")

    fun intentStart(ctx: Context): Intent = Intent(ctx, OpenClawForegroundService::class.java)

    fun enqueue(ctx: Context, cmd: UiCommand): Boolean {
      queue.add(cmd)
      // ensure service running
      val i = intentStart(ctx)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
      return true
    }

    fun getCachedState(): UiState = stateRef.get()
    fun getLastLogs(): String = lastLogs.get()
  }
}
