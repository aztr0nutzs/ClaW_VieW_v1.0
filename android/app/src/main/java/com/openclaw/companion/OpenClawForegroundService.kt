package com.openclaw.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import org.json.JSONArray
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OpenClawForegroundService : Service() {
  private var openClawGateway: OpenClawGateway? = null
  private var camsnapCapability: CamsnapCapability? = null

  override fun onCreate() {
    super.onCreate()
    Log.i(LOG_TAG, "SERVICE_START")

    val nodeId = NodeIdentity.getOrCreate(this)
    updateState { it.copy(nodeId = nodeId) }
    running.set(true)
    Log.i(LOG_TAG, "NODE_ID $nodeId")

    camsnapCapability = CamsnapCapability(this)

    ensureChannel()
    startForeground(NOTIF_ID, buildNotif("OpenClaw Companion running"))
    Log.i(LOG_TAG, "START_FOREGROUND notificationId=$NOTIF_ID")
    logBatteryOptimizationStatus()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i(LOG_TAG, "START_COMMAND startId=$startId flags=$flags intent=${intent?.action}")
    drainQueue()
    return START_STICKY
  }

  override fun onDestroy() {
    Log.i(LOG_TAG, "SERVICE_STOP")
    running.set(false)
    updateState { it.copy(connected = false, registered = false) }
    openClawGateway?.shutdown()
    openClawGateway = null
    camsnapCapability?.shutdown()
    camsnapCapability = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      stopForeground(true)
    }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun drainQueue() {
    while (true) {
      val cmd = queue.poll() ?: break
      when (cmd) {
        is UiCommand.Connect -> {
          Log.i(LOG_TAG, "SERVICE_CALL connectGateway")

          val controllerUrl = cmd.controllerUrl ?: stateRef.get().controllerUrl
          if (controllerUrl.isNullOrBlank()) {
            Log.w(LOG_TAG, "SERVICE_ERROR missing controllerUrl")
            updateState { it.copy(lastError = "MISSING_CONTROLLER_URL") }
            Log.i(LOG_TAG, "RESULT connectGateway ok=false code=MISSING_CONTROLLER_URL")
            continue
          }
          updateState { it.copy(controllerUrl = controllerUrl, lastError = null) }
          ensureGateway().connect(controllerUrl)
          Log.i(LOG_TAG, "RESULT connectGateway ok=true code=OK")
        }
        is UiCommand.Disconnect -> {
          Log.i(LOG_TAG, "SERVICE_CALL disconnectGateway")

          openClawGateway?.disconnect()
          updateState { it.copy(connected = false, registered = false, lastError = null) }
          Log.i(LOG_TAG, "RESULT disconnectGateway ok=true code=OK")
        }
        is UiCommand.Camsnap -> {
          Log.i(LOG_TAG, "SERVICE_CALL triggerCamsnap quality=${cmd.quality} maxBytes=${cmd.maxBytes}")
          Log.i(LOG_TAG, "RESULT triggerCamsnap ok=true code=QUEUED")
          executeCamsnap(requestId = null, quality = cmd.quality, maxBytes = cmd.maxBytes)
        }
      }
      Log.i(
        LOG_TAG,
        "STATE connected=${stateRef.get().connected} registered=${stateRef.get().registered} lastError=${stateRef.get().lastError}",
      )
    }
  }

  private fun ensureGateway(): OpenClawGateway {
    if (openClawGateway == null) {
      openClawGateway = OpenClawGateway(
        context = this,
        onState = { connected, registered, lastError ->
          updateState { it.copy(connected = connected, registered = registered, lastError = lastError) }
        },
        onLog = { message ->
          Log.i(GATEWAY_TAG, message)
          appendLog("$GATEWAY_TAG $message")
        },
        onProtocolLog = { message ->
          Log.i(PROTOCOL_TAG, message)
          appendLog("$PROTOCOL_TAG $message")
        },
        onHeartbeat = { heartbeat ->
          updateState { it.copy(lastHeartbeat = heartbeat) }
        },
        onCapabilityRequest = { requestId, capability, args ->
          Log.i(PROTOCOL_TAG, "CAPABILITY_REQUEST capability=$capability requestId=$requestId args=$args")
          handleCapabilityRequest(requestId, capability, args)
        },
        getNodeId = { stateRef.get().nodeId ?: "unknown" },
        getCapabilities = { capabilityManifest() },
      )
    }
    return openClawGateway!!
  }

  private fun updateState(transform: (UiState) -> UiState) {
    val next = transform(stateRef.get())
    stateRef.set(next)
    pushStateToDashboard(next)
  }

  private fun pushStateToDashboard(state: UiState) {
    val bridge = bridgeRef.get()?.get() ?: return
    bridge.pushStateToJs(state.toJson().toString())
  }

  private fun appendLog(message: String) {
    synchronized(logLock) {
      val next = buildString {
        val existing = lastLogs.get()
        if (existing.isNotEmpty()) {
          append(existing)
          append("\n")
        }
        append(message)
      }
      val trimmed = if (next.length > LOG_BUFFER_LIMIT) {
        next.takeLast(LOG_BUFFER_LIMIT)
      } else {
        next
      }
      lastLogs.set(trimmed)
    }
  }

  private fun capabilityManifest(): JSONArray {
    return CapabilityRegistry.manifest()
  }

  private fun handleCapabilityRequest(requestId: String?, capability: String, args: org.json.JSONObject) {
    when (capability) {
      "camsnap" -> {
        val quality = args.optInt("quality", 85)
        val maxBytes = args.optInt("maxBytes", 600000)
        executeCamsnap(requestId, quality, maxBytes)
      }
      else -> {
        val result = CapabilityResult(
          requestId = requestId,
          capability = capability,
          ok = false,
          code = "CAPABILITY_UNKNOWN",
          message = "Unknown capability: $capability",
        )
        val msg = "CAPABILITY_UNKNOWN capability=$capability requestId=$requestId"
        Log.w("OpenClawProtocol", msg)
        appendLog("OpenClawProtocol $msg")
        updateState { it.copy(lastCapabilityResult = result, lastError = result.code) }
        openClawGateway?.sendCapabilityResult(result)
      }
    }
  }

  private fun executeCamsnap(requestId: String?, quality: Int, maxBytes: Int) {
    val capability = CapabilityRegistry.get("camsnap")
    if (capability == null) {
      val result = CapabilityResult(requestId, "camsnap", false, "CAPABILITY_UNKNOWN", "Capability not supported: camsnap")
      updateState { it.copy(lastCapabilityResult = result, lastError = result.code) }
      openClawGateway?.sendCapabilityResult(result)
      return
    }
    val missingPermissions = CapabilityRegistry.missingPermissions(this, capability)
    if (missingPermissions.isNotEmpty()) {
      val result = CapabilityResult(
        requestId = requestId,
        capability = "camsnap",
        ok = false,
        code = "PERMISSION_DENIED",
        message = "Missing required permissions",
        data = org.json.JSONObject().put("missingPermissions", missingPermissions),
      )
      val msg = "PERMISSION_DENIED missing=$missingPermissions"
      Log.w(CAMSNAP_TAG, msg)
      appendLog("$CAMSNAP_TAG $msg")
      updateState { it.copy(lastCapabilityResult = result, lastError = result.code) }
      openClawGateway?.sendCapabilityResult(result)
      return
    }
    val camsnap = camsnapCapability ?: run {
      val result = CapabilityResult(requestId, "camsnap", false, "CAMERA_UNAVAILABLE", "Camera unavailable")
      updateState { it.copy(lastCapabilityResult = result, lastError = result.code) }
      openClawGateway?.sendCapabilityResult(result)
      return
    }
    camsnap.capture(quality, maxBytes) { rawResult ->
      val finalResult = rawResult.copy(requestId = requestId)
      if (finalResult.ok) {
        val msg = "CAPTURE_OK bytes=${finalResult.data?.optInt(\"bytes\")}"
        Log.i(CAMSNAP_TAG, msg)
        appendLog("$CAMSNAP_TAG $msg")
      } else {
        val msg = "CAPTURE_FAIL code=${finalResult.code} message=${finalResult.message}"
        Log.w(CAMSNAP_TAG, msg)
        appendLog("$CAMSNAP_TAG $msg")
      }
      updateState { it.copy(lastCapabilityResult = finalResult, lastError = if (finalResult.ok) null else finalResult.code) }
      val gateway = openClawGateway
      if (gateway != null) {
        val outbound = if (requestId != null) finalResult else finalResult.copy(requestId = UUID.randomUUID().toString())
        gateway.sendCapabilityResult(outbound)
      }
    }
  }

  private fun logBatteryOptimizationStatus() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      Log.i(LOG_TAG, "BATTERY_OPTIMIZATION_UNSUPPORTED sdk=${Build.VERSION.SDK_INT}")
      return
    }
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoring = powerManager.isIgnoringBatteryOptimizations(packageName)
    if (ignoring) {
      Log.i(LOG_TAG, "BATTERY_OPTIMIZATION_OK package=$packageName")
    } else {
      Log.w(LOG_TAG, "BATTERY_OPTIMIZATION_PROMPT package=$packageName")
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
    private const val LOG_BUFFER_LIMIT = 4000
    private const val LOG_TAG = "OPENCLAW_SERVICE"
    private const val GATEWAY_TAG = "OPENCLAW_GATEWAY"
    private const val PROTOCOL_TAG = "OPENCLAW_PROTOCOL"
    private const val CAMSNAP_TAG = "OPENCLAW_CAMSNAP"

    private val queue = ConcurrentLinkedQueue<UiCommand>()
    private val stateRef = AtomicReference(UiState())
    private val lastLogs = AtomicReference("")
    private val running = AtomicBoolean(false)
    private val logLock = Any()
    private val bridgeRef = AtomicReference<WeakReference<OpenClawBridge>?>(null)

    fun intentStart(ctx: Context): Intent = Intent(ctx, OpenClawForegroundService::class.java)

    fun enqueue(ctx: Context, cmd: UiCommand): Boolean {
      queue.add(cmd)
      // ensure service running
      val i = intentStart(ctx)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
      return true
    }

    fun getCachedState(): UiState {
      val state = stateRef.get()
      return if (running.get()) {
        state
      } else {
        state.copy(connected = false, registered = false)
      }
    }

    fun getLastLogs(): String = lastLogs.get()

    fun attachBridge(bridge: OpenClawBridge?) {
      bridgeRef.set(bridge?.let { WeakReference(it) })
    }
  }
}
