package com.openclaw.clawview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.openclaw.clawview.camera.CameraManager
import com.openclaw.clawview.databinding.ActivityMainBinding
import com.openclaw.clawview.service.NodeForegroundService
import com.openclaw.clawview.storage.NodeIdentityStorage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "MainActivity"

/**
 * Main activity for the OpenClaw node application.
 * 
 * Implements:
 * - Comprehensive logging (logs before UI updates)
 * - Permission-aware camera access
 * - Foreground service management
 * - Node identity display
 * 
 * NEVER simulates success - all operations are real.
 */
class MainActivity : AppCompatActivity(), CameraManager.CameraListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nodeStorage: NodeIdentityStorage
    private lateinit var cameraManager: CameraManager
    
    private val logLines = ArrayDeque<String>(50) // Circular buffer for log lines
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Permission launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        log("Camera permission result: ${if (isGranted) "GRANTED" else "DENIED"}")
        if (isGranted) {
            log("Permission granted, starting camera")
            startCamera()
        } else {
            log("ERROR: Camera permission denied by user")
            showToast(getString(R.string.permission_camera_required))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        log("Notification permission result: ${if (isGranted) "GRANTED" else "DENIED"}")
        if (!isGranted) {
            log("WARNING: Notification permission denied, service may not show notification")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        log("=== MainActivity onCreate ===")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize components
        nodeStorage = NodeIdentityStorage(applicationContext)
        cameraManager = CameraManager(applicationContext)
        cameraManager.setListener(this)
        
        log("Components initialized")
        
        // Setup UI
        setupUI()
        
        // Load node identity
        loadNodeIdentity()
        
        // Request permissions if needed
        checkAndRequestPermissions()
        
        log("MainActivity initialization complete")
    }

    /**
     * Setup UI listeners and initial state.
     */
    private fun setupUI() {
        log("Setting up UI")
        
        binding.btnStartService.setOnClickListener {
            log("Start Service button clicked")
            startNodeService()
        }
        
        binding.btnStopService.setOnClickListener {
            log("Stop Service button clicked")
            stopNodeService()
        }
        
        updateServiceStatus()
    }

    /**
     * Load and display node identity.
     */
    private fun loadNodeIdentity() {
        log("Loading node identity from storage")
        
        lifecycleScope.launch {
            try {
                // Ensure node ID is created and saved
                val nodeId = nodeStorage.getOrCreateNodeId()
                log("Node ID initialized: $nodeId")
                
                // Observe node ID for updates
                nodeStorage.nodeIdFlow.collectLatest { id ->
                    if (id.isNotEmpty()) {
                        log("Node ID updated: $id")
                        binding.tvNodeId.text = id
                    }
                }
            } catch (e: Exception) {
                log("ERROR loading node ID: ${e.message}")
                Log.e(TAG, "Failed to load node ID", e)
            }
        }
        
        lifecycleScope.launch {
            try {
                // Observe server URL
                nodeStorage.serverUrlFlow.collectLatest { serverUrl ->
                    if (!serverUrl.isNullOrBlank()) {
                        log("Server URL loaded: $serverUrl")
                        binding.etServerUrl.setText(serverUrl)
                    } else {
                        log("No server URL saved")
                    }
                }
            } catch (e: Exception) {
                log("ERROR loading server URL: ${e.message}")
                Log.e(TAG, "Failed to load server URL", e)
            }
        }
    }

    /**
     * Check and request necessary permissions.
     */
    private fun checkAndRequestPermissions() {
        log("Checking permissions")
        
        // Check camera permission
        if (!cameraManager.hasPermission()) {
            log("Camera permission not granted, requesting...")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            log("Camera permission already granted")
            startCamera()
        }
        
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                log("Notification permission not granted, requesting...")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                log("Notification permission already granted")
            }
        }
    }

    /**
     * Start camera preview.
     */
    private fun startCamera() {
        log("Starting camera preview")
        
        if (!cameraManager.hasPermission()) {
            log("ERROR: Cannot start camera without permission")
            return
        }
        
        try {
            cameraManager.startCamera(binding.previewView, this)
        } catch (e: Exception) {
            log("ERROR starting camera: ${e.message}")
            Log.e(TAG, "Failed to start camera", e)
        }
    }

    /**
     * Start the node foreground service.
     */
    private fun startNodeService() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        
        if (serverUrl.isBlank()) {
            log("ERROR: Cannot start service - Server URL is empty")
            showToast("Please enter a server URL")
            return
        }
        
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            log("ERROR: Invalid server URL format: $serverUrl")
            showToast("Server URL must start with ws:// or wss://")
            return
        }
        
        log("Starting node service with URL: $serverUrl")
        
        // Save server URL
        lifecycleScope.launch {
            try {
                nodeStorage.saveServerUrl(serverUrl)
                log("Server URL saved to storage")
            } catch (e: Exception) {
                log("ERROR saving server URL: ${e.message}")
            }
        }
        
        // Start foreground service
        val intent = Intent(this, NodeForegroundService::class.java).apply {
            action = NodeForegroundService.ACTION_START
            putExtra(NodeForegroundService.EXTRA_SERVER_URL, serverUrl)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            log("Node service start command sent")
            updateServiceStatus()
        } catch (e: Exception) {
            log("ERROR starting service: ${e.message}")
            Log.e(TAG, "Failed to start service", e)
            showToast("Failed to start service: ${e.message}")
        }
    }

    /**
     * Stop the node foreground service.
     */
    private fun stopNodeService() {
        log("Stopping node service")
        
        val intent = Intent(this, NodeForegroundService::class.java).apply {
            action = NodeForegroundService.ACTION_STOP
        }
        
        try {
            startService(intent)
            log("Node service stop command sent")
            updateServiceStatus()
        } catch (e: Exception) {
            log("ERROR stopping service: ${e.message}")
            Log.e(TAG, "Failed to stop service", e)
        }
    }

    /**
     * Update UI based on service status.
     */
    private fun updateServiceStatus() {
        val isRunning = NodeForegroundService.isRunning
        
        log("Service status: ${if (isRunning) "RUNNING" else "STOPPED"}")
        
        binding.tvStatus.text = if (isRunning) {
            getString(R.string.node_status_connected)
        } else {
            getString(R.string.node_status_disconnected)
        }
        
        binding.btnStartService.isEnabled = !isRunning
        binding.btnStopService.isEnabled = isRunning
    }

    // CameraManager.CameraListener implementations
    override fun onCameraReady() {
        log("Camera ready")
        runOnUiThread {
            showToast(getString(R.string.camera_ready))
        }
    }

    override fun onCameraError(error: String) {
        log("Camera ERROR: $error")
        runOnUiThread {
            showToast(getString(R.string.camera_error, error))
        }
    }

    override fun onPermissionRequired() {
        log("Camera permission required")
        runOnUiThread {
            showToast(getString(R.string.permission_camera_required))
        }
    }

    /**
     * Add log entry with timestamp.
     */
    private fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message"
        
        // Log to Logcat
        Log.i(TAG, message)
        
        // Add to circular buffer (O(1) operation)
        synchronized(logLines) {
            if (logLines.size >= 50) {
                logLines.removeFirst()
            }
            logLines.addLast(logLine)
        }
        
        // Update UI on main thread
        runOnUiThread {
            val logText = synchronized(logLines) {
                logLines.joinToString("\n")
            }
            binding.tvLogs.text = logText
            binding.scrollViewLogs.post {
                binding.scrollViewLogs.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    /**
     * Show toast message.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        log("MainActivity resumed")
        updateServiceStatus()
    }

    override fun onPause() {
        super.onPause()
        log("MainActivity paused")
    }

    override fun onDestroy() {
        log("MainActivity destroyed")
        cameraManager.cleanup()
        super.onDestroy()
    }
}
