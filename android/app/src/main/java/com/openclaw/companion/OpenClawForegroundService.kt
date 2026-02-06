package com.openclaw.companion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OpenClawForegroundService : LifecycleService() {
  private var gatewayClient: GatewayClient? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var imageCapture: ImageCapture? = null
  private var imageCaptureQuality: Int = -1
  private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private val cameraLock = Any()

  override fun onCreate() {
    super.onCreate()
    Log.i(LOG_TAG, "SERVICE_START")

    val nodeId = NodeIdentity.getOrCreate(this)
    updateState { it.copy(nodeId = nodeId, cameraPermissionGranted = hasCameraPermission()) }
    running.set(true)
    Log.i(LOG_TAG, "NODE_ID $nodeId")

    ensureChannel()
    startForeground(NOTIF_ID, buildNotif("OpenClaw Companion running"))
    Log.i(LOG_TAG, "START_FOREGROUND notificationId=$NOTIF_ID")
    logBatteryOptimizationStatus()
    initCameraProvider()
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
    gatewayClient?.shutdown()
    gatewayClient = null
    cameraProvider?.unbindAll()
    cameraExecutor.shutdown()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      stopForeground(true)
    }
    super.onDestroy()
  }

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
            continue
          }
          updateState { it.copy(controllerUrl = controllerUrl, lastError = null) }
          ensureGatewayClient().connect(controllerUrl)
        }
        is UiCommand.Disconnect -> {
          Log.i(LOG_TAG, "SERVICE_CALL disconnectGateway")

          gatewayClient?.disconnect()
          updateState { it.copy(connected = false, registered = false, lastError = null) }
        }
        is UiCommand.Camsnap -> {
          Log.i(LOG_TAG, "SERVICE_CALL triggerCamsnap quality=${cmd.quality} maxBytes=${cmd.maxBytes}")
          triggerCamsnap("ui-${System.currentTimeMillis()}", cmd.quality, cmd.maxBytes)
        }
        is UiCommand.CameraPermission -> {
          Log.i(LOG_TAG, "SERVICE_CALL cameraPermission granted=${cmd.granted}")
          updateState {
            it.copy(
              cameraPermissionGranted = cmd.granted,
              lastError = if (cmd.granted) null else "CAMERA_PERMISSION_DENIED",
            )
          }
        }
      }
      Log.i(
        LOG_TAG,
        "STATE connected=${stateRef.get().connected} registered=${stateRef.get().registered} lastError=${stateRef.get().lastError}",
      )
    }
  }

  private fun ensureGatewayClient(): GatewayClient {
    if (gatewayClient == null) {
      gatewayClient = GatewayClient(
        onState = { connected, registered, lastError ->
          updateState { it.copy(connected = connected, registered = registered, lastError = lastError) }
        },
        onLog = { message ->
          Log.i(GATEWAY_TAG, message)
          appendLog("$GATEWAY_TAG $message")
        },
        getNodeId = { stateRef.get().nodeId ?: "unknown" },
        getCapabilities = { capabilityManifest() },
        onHeartbeat = {
          updateState { it.copy(lastHeartbeat = Instant.now().toString()) }
        },
        onCapabilityRequest = { request ->
          handleCapabilityRequest(request)
        },
      )
    }
    return gatewayClient!!
  }

  private fun updateState(transform: (UiState) -> UiState) {
    val next = transform(stateRef.get())
    stateRef.set(next)
    stateSink.get()?.invoke(next.toJson().toString())
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
    return JSONArray()
      .put("camsnap")
  }

  private fun hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }

  private fun initCameraProvider() {
    val providerFuture = ProcessCameraProvider.getInstance(this)
    providerFuture.addListener(
      {
        cameraProvider = providerFuture.get()
        Log.i(LOG_TAG, "CAMERA_PROVIDER_READY")
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun handleCapabilityRequest(request: CapabilityRequest) {
    if (request.capability != "camsnap") {
      sendCapabilityResult(
        requestId = request.requestId,
        capability = request.capability,
        ok = false,
        result = null,
        error = JSONObject().put("code", "CAPABILITY_UNKNOWN").put("message", "Unknown capability"),
      )
      return
    }
    val quality = request.args.optInt("quality", 85)
    val maxBytes = request.args.optInt("maxBytes", 600000)
    triggerCamsnap(request.requestId, quality, maxBytes)
  }

  private fun triggerCamsnap(requestId: String, quality: Int, maxBytes: Int) {
    val state = stateRef.get()
    if (!state.connected || !state.registered) {
      updateState { it.copy(lastError = "NOT_CONNECTED") }
      sendCapabilityResult(
        requestId = requestId,
        capability = "camsnap",
        ok = false,
        result = null,
        error = JSONObject().put("code", "NOT_CONNECTED").put("message", "Gateway not connected"),
      )
      return
    }
    if (!hasCameraPermission()) {
      updateState { it.copy(lastError = "CAMERA_PERMISSION_DENIED", cameraPermissionGranted = false) }
      sendCapabilityResult(
        requestId = requestId,
        capability = "camsnap",
        ok = false,
        result = null,
        error = JSONObject().put("code", "PERMISSION_DENIED").put("message", "Camera permission denied"),
      )
      return
    }
    val capture = prepareImageCapture(quality)
    if (capture == null) {
      updateState { it.copy(lastError = "CAMERA_NOT_READY") }
      sendCapabilityResult(
        requestId = requestId,
        capability = "camsnap",
        ok = false,
        result = null,
        error = JSONObject().put("code", "CAMERA_NOT_READY").put("message", "Camera is not ready"),
      )
      return
    }

    val outputFile = File.createTempFile("camsnap_", ".jpg", cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    capture.takePicture(
      outputOptions,
      cameraExecutor,
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          val rawBytes = runCatching { outputFile.readBytes() }.getOrNull()
          outputFile.delete()
          if (rawBytes == null) {
            updateState { it.copy(lastError = "CAMSNAP_READ_FAILED") }
            sendCapabilityResult(
              requestId = requestId,
              capability = "camsnap",
              ok = false,
              result = null,
              error = JSONObject().put("code", "CAMSNAP_READ_FAILED").put("message", "Failed to read image"),
            )
            return
          }

          val tuned = tuneImageSize(rawBytes, quality, maxBytes)
          val bytes = tuned.bytes
          val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
          val result = JSONObject()
            .put("mimeType", "image/jpeg")
            .put("bytesB64", encoded)
            .put("sizeBytes", bytes.size)
            .put("width", tuned.width)
            .put("height", tuned.height)
            .put("quality", tuned.quality)

          sendCapabilityResult(
            requestId = requestId,
            capability = "camsnap",
            ok = true,
            result = result,
            error = null,
          )
        }

        override fun onError(exception: ImageCaptureException) {
          outputFile.delete()
          updateState { it.copy(lastError = "CAMSNAP_FAILED") }
          sendCapabilityResult(
            requestId = requestId,
            capability = "camsnap",
            ok = false,
            result = null,
            error = JSONObject().put("code", "CAMSNAP_FAILED").put("message", exception.message ?: "Capture failed"),
          )
        }
      },
    )
  }

  private fun prepareImageCapture(quality: Int): ImageCapture? {
    val provider = cameraProvider ?: return null
    synchronized(cameraLock) {
      if (imageCapture == null || imageCaptureQuality != quality) {
        val capture = ImageCapture.Builder()
          .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
          .setJpegQuality(quality.coerceIn(1, 100))
          .build()
        return try {
          provider.unbindAll()
          provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
          imageCapture = capture
          imageCaptureQuality = quality
          capture
        } catch (e: Exception) {
          Log.e(LOG_TAG, "CAMERA_BIND_FAILED ${e.message}")
          null
        }
      }
      return imageCapture
    }
  }

  private data class TunedImage(val bytes: ByteArray, val width: Int, val height: Int, val quality: Int)

  private fun tuneImageSize(rawBytes: ByteArray, baseQuality: Int, maxBytes: Int): TunedImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
    if (maxBytes <= 0 || rawBytes.size <= maxBytes) {
      return TunedImage(rawBytes, bounds.outWidth, bounds.outHeight, baseQuality)
    }
    val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
      ?: return TunedImage(rawBytes, bounds.outWidth, bounds.outHeight, baseQuality)
    val width = bitmap.width
    val height = bitmap.height
    var quality = baseQuality.coerceIn(1, 100)
    var bytes = rawBytes
    while (quality > 30 && bytes.size > maxBytes) {
      quality = (quality - 10).coerceAtLeast(30)
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
      bytes = out.toByteArray()
    }
    bitmap.recycle()
    return TunedImage(bytes, width, height, quality)
  }

  private fun sendCapabilityResult(
    requestId: String,
    capability: String,
    ok: Boolean,
    result: JSONObject?,
    error: JSONObject?,
  ) {
    val sent = gatewayClient?.sendCapabilityResult(requestId, capability, ok, result, error) == true
    val errorCode = error?.optString("code", null)
    val resolvedError = when {
      !sent && errorCode == null -> "GATEWAY_NOT_CONNECTED"
      else -> errorCode
    }
    updateState {
      it.copy(
        lastCapability = capability,
        lastCapabilityOk = ok,
        lastCapabilityError = resolvedError,
        lastCapabilityTs = Instant.now().toString(),
        lastError = if (!ok) resolvedError else if (!sent) "GATEWAY_NOT_CONNECTED" else null,
      )
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

    private val queue = ConcurrentLinkedQueue<UiCommand>()
    private val stateRef = AtomicReference(UiState())
    private val lastLogs = AtomicReference("")
    private val running = AtomicBoolean(false)
    private val stateSink = AtomicReference<((String) -> Unit)?>(null)
    private val logLock = Any()

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

    fun registerStateSink(sink: ((String) -> Unit)?) {
      stateSink.set(sink)
      sink?.invoke(getCachedState().toJson().toString())
    }
  }
}
