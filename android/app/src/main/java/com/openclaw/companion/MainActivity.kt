package com.openclaw.companion

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
  private val cameraPermissionRequestCode = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Start the ForegroundService that owns the gateway/capabilities.
    ContextCompat.startForegroundService(this, OpenClawForegroundService.intentStart(this))
    ensureCameraPermission()

    val web = WebView(this)
    web.setBackgroundColor(Color.BLACK)

    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      cacheMode = WebSettings.LOAD_NO_CACHE
      mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
      allowFileAccess = true
      allowFileAccessFromFileURLs = true
      allowUniversalAccessFromFileURLs = true
      mediaPlaybackRequiresUserGesture = false
      useWideViewPort = true
      loadWithOverviewMode = true
      setSupportZoom(false)
    }

    web.webChromeClient = object : WebChromeClient() {
      override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
        Log.d("OPENCLAW_UI", "js: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
        return true
      }
    }

    web.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView, url: String) {
        Log.i("OPENCLAW_UI", "GUI_LOADED openclaw_dash.html url=$url")
        // Optional: request a state push from service here if you implement it.
      }
    }

    // JS bridge: must forward to the ForegroundService.
    web.addJavascriptInterface(OpenClawBridge(this, web), "OpenClawBridge")

    val assetUrl = "file:///android_asset/openclaw_dash.html"
    web.loadUrl(assetUrl)

    setContentView(web)
  }

  private fun ensureCameraPermission() {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (granted) {
      OpenClawForegroundService.enqueue(this, UiCommand.CameraPermission(true))
      return
    }
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraPermissionRequestCode)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == cameraPermissionRequestCode) {
      val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
      OpenClawForegroundService.enqueue(this, UiCommand.CameraPermission(granted))
    }
  }
}
