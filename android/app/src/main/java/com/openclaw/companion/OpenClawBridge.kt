package com.openclaw.companion

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * JS bridge used by openclaw_dash.html. This MUST forward into the ForegroundService.
 * All methods return a JSON envelope string:
 * { ok:boolean, code:string, message:string, data?:object }
 */
class OpenClawBridge(
  private val context: Context,
  private val webView: WebView,
) {

  @JavascriptInterface
  fun connectGateway(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT connectGateway args=$argsJson")
    val args = runCatching { JSONObject(argsJson) }.getOrNull()
    val controllerUrl = args?.optString("controllerUrl", null)
    return UiEventController.connectGateway(context, controllerUrl)
  }

  @JavascriptInterface
  fun disconnectGateway(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT disconnectGateway args=$argsJson")
    return UiEventController.disconnectGateway(context)
  }

  @JavascriptInterface
  fun triggerCamsnap(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT triggerCamsnap args=$argsJson")
    val capability = CapabilityRegistry.get("camsnap")
      ?: return envelope(false, "CAPABILITY_UNKNOWN", "Capability not supported: camsnap")
    val missingPermissions = CapabilityRegistry.missingPermissions(context, capability)
    if (missingPermissions.isNotEmpty()) {
      val data = JSONObject()
        .put("capability", capability.name)
        .put("missingPermissions", missingPermissions)
      return envelope(false, "PERMISSION_DENIED", "Missing required permissions", data)
    }
    val args = runCatching { JSONObject(argsJson) }.getOrNull()
    val quality = args?.optInt("quality", 85) ?: 85
    val maxBytes = args?.optInt("maxBytes", 600000) ?: 600000
    return UiEventController.triggerCamsnap(context, quality, maxBytes)
  }

  @JavascriptInterface
  fun requestStatus(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT requestStatus args=$argsJson")
    return UiEventController.requestStatus()
  }

  @JavascriptInterface
  fun getLastLogs(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT getLastLogs args=$argsJson")
    return UiEventController.getLastLogs()
  }

  /** Optional: service can call this to push state into JS. */
  fun pushStateToJs(stateJson: String) {
    webView.post {
      val js = "window.onStateUpdate(${JSONObject.quote(stateJson)});"

      webView.evaluateJavascript(js, null)
    }
  }

  private fun envelope(ok: Boolean, code: String, message: String, data: JSONObject? = null): String {
    val o = JSONObject()
      .put("ok", ok)
      .put("code", code)
      .put("message", message)
    if (data != null) o.put("data", data)
    return o.toString()
  }
}
