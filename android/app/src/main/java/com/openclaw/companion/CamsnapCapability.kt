package com.openclaw.companion

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CamsnapCapability(private val context: Context) : LifecycleOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)
  private val cameraExecutor = Executors.newSingleThreadExecutor()
  private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context)
  private val cameraLock = Any()
  private var cameraProvider: ProcessCameraProvider? = null
  private var imageCapture: ImageCapture? = null

  init {
    lifecycleRegistry.currentState = Lifecycle.State.CREATED
  }

  override fun getLifecycle(): Lifecycle = lifecycleRegistry

  fun shutdown() {
    synchronized(cameraLock) {
      imageCapture = null
      cameraProvider?.unbindAll()
      cameraProvider = null
      lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
    cameraExecutor.shutdown()
  }

  fun capture(quality: Int, maxBytes: Int, callback: (CapabilityResult) -> Unit) {
    cameraExecutor.execute {
      val capture = ensureCamera(quality, callback) ?: return@execute
      val tempFile = File.createTempFile("camsnap_", ".jpg", context.cacheDir)
      val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
      capture.takePicture(
        outputOptions,
        callbackExecutor,
        object : ImageCapture.OnImageSavedCallback {
          override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            cameraExecutor.execute {
              val bytes = runCatching { tempFile.readBytes() }.getOrElse {
                tempFile.delete()
                callbackExecutor.execute {
                  callback(
                    CapabilityResult(
                      requestId = null,
                      capability = "camsnap",
                      ok = false,
                      code = "CAPTURE_READ_FAILED",
                      message = "Failed to read captured image",
                    ),
                  )
                }
                return@execute
              }
              tempFile.delete()
              if (bytes.size > maxBytes) {
                callbackExecutor.execute {
                  callback(
                    CapabilityResult(
                      requestId = null,
                      capability = "camsnap",
                      ok = false,
                      code = "IMAGE_TOO_LARGE",
                      message = "Captured image exceeds maxBytes",
                      data = JSONObject().put("bytes", bytes.size).put("maxBytes", maxBytes),
                    ),
                  )
                }
                return@execute
              }
              val imageId = UUID.randomUUID().toString()
              val encoded = Base64.getEncoder().encodeToString(bytes)
              val data = JSONObject()
                .put("imageId", imageId)
                .put("bytes", bytes.size)
                .put("mime", "image/jpeg")
                .put("jpegBase64", encoded)
              callbackExecutor.execute {
                callback(
                  CapabilityResult(
                    requestId = null,
                    capability = "camsnap",
                    ok = true,
                    code = "OK",
                    message = "Captured image",
                    data = data,
                  ),
                )
              }
            }
          }

          override fun onError(exception: ImageCaptureException) {
            tempFile.delete()
            callback(
              CapabilityResult(
                requestId = null,
                capability = "camsnap",
                ok = false,
                code = "CAPTURE_FAILED",
                message = exception.message ?: "Capture failed",
              ),
            )
          }
        },
      )
    }
  }

  private fun ensureCamera(quality: Int, callback: (CapabilityResult) -> Unit): ImageCapture? {
    synchronized(cameraLock) {
      if (cameraProvider == null) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = runCatching { providerFuture.get(5, TimeUnit.SECONDS) }.getOrElse { err ->
          Log.e(LOG_TAG, "CAMERA_UNAVAILABLE", err)
          callback(
            CapabilityResult(
              requestId = null,
              capability = "camsnap",
              ok = false,
              code = "CAMERA_UNAVAILABLE",
              message = err.message ?: "Camera unavailable",
            ),
          )
          return null
        }
        cameraProvider = provider
      }

      val provider = cameraProvider ?: return null
      val jpegQuality = quality.coerceIn(1, 100)
      val capture = ImageCapture.Builder()
        .setJpegQuality(jpegQuality)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
      runCatching {
        provider.unbindAll()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
      }.onFailure { err ->
        Log.e(LOG_TAG, "CAMERA_BIND_FAILED", err)
        callback(
          CapabilityResult(
            requestId = null,
            capability = "camsnap",
            ok = false,
            code = "CAMERA_BIND_FAILED",
            message = err.message ?: "Camera bind failed",
          ),
        )
        return null
      }
      imageCapture = capture
      return capture
    }
  }

  companion object {
    private const val LOG_TAG = "OPENCLAW_CAMSNAP"
  }
}
