package com.openclaw.clawview.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraManager"

/**
 * Permission-aware CameraX manager.
 * REFUSES to operate without proper camera permission.
 * Provides real camera preview and image capture capabilities.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    interface CameraListener {
        fun onCameraReady()
        fun onCameraError(error: String)
        fun onPermissionRequired()
    }

    private var listener: CameraListener? = null

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Set listener for camera events.
     */
    fun setListener(listener: CameraListener) {
        this.listener = listener
    }

    /**
     * Check if camera permission is granted.
     */
    fun hasPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Camera permission status: ${if (hasPermission) "GRANTED" else "DENIED"}")
        return hasPermission
    }

    /**
     * Start camera with preview.
     * REQUIRES camera permission - will FAIL if not granted.
     */
    fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        // CRITICAL: Check permission first
        if (!hasPermission()) {
            val error = "PERMISSION DENIED: Camera permission is required to start camera"
            Log.e(TAG, error)
            listener?.onPermissionRequired()
            listener?.onCameraError(error)
            return
        }

        Log.i(TAG, "Starting camera with permission granted")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                // Bind camera to lifecycle
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView, lifecycleOwner)
            } catch (e: Exception) {
                val error = "FAILED to start camera: ${e.message}"
                Log.e(TAG, error, e)
                listener?.onCameraError(error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Bind camera use cases (preview and image capture).
     */
    private fun bindCameraUseCases(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = this.cameraProvider ?: run {
            val error = "FAILED: CameraProvider is null"
            Log.e(TAG, error)
            listener?.onCameraError(error)
            return
        }

        // Preview use case
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Select back camera as default
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            Log.i(TAG, "Camera successfully bound to lifecycle")
            listener?.onCameraReady()

        } catch (e: Exception) {
            val error = "FAILED to bind camera use cases: ${e.message}"
            Log.e(TAG, error, e)
            listener?.onCameraError(error)
        }
    }

    /**
     * Stop camera and release resources.
     */
    fun stopCamera() {
        Log.i(TAG, "Stopping camera")
        cameraProvider?.unbindAll()
        camera = null
    }

    /**
     * Capture an image.
     * Returns false if camera is not ready or permission not granted.
     */
    fun captureImage(onImageCaptured: (ImageProxy) -> Unit): Boolean {
        if (!hasPermission()) {
            Log.e(TAG, "FAILED to capture image: Camera permission not granted")
            return false
        }

        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "FAILED to capture image: ImageCapture not initialized")
            return false
        }

        try {
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        Log.d(TAG, "Image captured successfully")
                        onImageCaptured(image)
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val error = "FAILED to capture image: ${exception.message}"
                        Log.e(TAG, error, exception)
                        listener?.onCameraError(error)
                    }
                }
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to capture image: ${e.message}", e)
            return false
        }
    }

    /**
     * Get camera info if available.
     */
    fun getCameraInfo(): CameraInfo? {
        return camera?.cameraInfo
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up camera resources")
        stopCamera()
        cameraExecutor.shutdown()
    }
}
