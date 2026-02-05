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
    val controllerUrl = args?.optString("controllerUrl")?.takeIf { it.isNotBlank() }
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Connect(controllerUrl))
    return envelope(ok, if (ok) "OK" else "SERVICE_NOT_RUNNING", if (ok) "Connect queued" else "Service not running")
  }

  @JavascriptInterface
  fun disconnectGateway(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT disconnectGateway args=$argsJson")
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Disconnect)
    return envelope(ok, if (ok) "OK" else "SERVICE_NOT_RUNNING", if (ok) "Disconnect queued" else "Service not running")
  }

  @JavascriptInterface
  fun triggerCamsnap(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT triggerCamsnap args=$argsJson")
    val args = runCatching { JSONObject(argsJson) }.getOrNull()
    val quality = args?.optInt("quality", 85) ?: 85
    val maxBytes = args?.optInt("maxBytes", 600000) ?: 600000
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Camsnap(quality, maxBytes))
    return envelope(ok, if (ok) "OK" else "SERVICE_NOT_RUNNING", if (ok) "Camsnap queued" else "Service not running")
  }

  @JavascriptInterface
  fun requestStatus(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT requestStatus args=$argsJson")
    val state = OpenClawForegroundService.getUiStateSnapshot()
    val data = JSONObject().put("state", state.toJson())
    return envelope(true, "OK", "State", data)
  }

  @JavascriptInterface
  fun getLastLogs(argsJson: String): String {
    Log.i("OPENCLAW_UI", "UI_EVENT getLastLogs args=$argsJson")
    val logs = OpenClawForegroundService.getLastLogs()
    val data = JSONObject().put("lastLogs", logs)
    return envelope(true, "OK", "Logs", data)
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
