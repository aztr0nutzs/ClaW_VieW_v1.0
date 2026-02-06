package com.openclaw.companion

import android.content.Context
import android.util.Log
import org.json.JSONObject

object UiEventController {
  fun connectGateway(context: Context, controllerUrl: String?): String {
    val trimmed = controllerUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
      Log.w(LOG_TAG, "UI_RESULT connectGateway ok=false code=MISSING_CONTROLLER_URL")
      return UiResponse(false, "MISSING_CONTROLLER_URL", "Controller URL is required")
        .withState(OpenClawForegroundService.getCachedState())
        .toJsonString()
    }
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Connect(trimmed))
    Log.i(LOG_TAG, "UI_RESULT connectGateway ok=$ok code=${if (ok) "OK" else "SERVICE_NOT_RUNNING"}")
    return UiResponse(ok, if (ok) "OK" else "SERVICE_NOT_RUNNING", if (ok) "Connect queued" else "Service not running")
      .withState(OpenClawForegroundService.getCachedState())
      .toJsonString()
  }

  fun disconnectGateway(context: Context): String {
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Disconnect)
    Log.i(LOG_TAG, "UI_RESULT disconnectGateway ok=$ok code=${if (ok) "OK" else "SERVICE_NOT_RUNNING"}")
    return UiResponse(ok, if (ok) "OK" else "SERVICE_NOT_RUNNING", if (ok) "Disconnect queued" else "Service not running")
      .withState(OpenClawForegroundService.getCachedState())
      .toJsonString()
  }

  fun triggerCamsnap(context: Context, quality: Int, maxBytes: Int): String {
    val ok = OpenClawForegroundService.enqueue(context, UiCommand.Camsnap(quality, maxBytes))
    Log.i(LOG_TAG, "UI_RESULT triggerCamsnap ok=$ok code=${if (ok) "QUEUED" else "SERVICE_NOT_RUNNING"}")
    return UiResponse(ok, if (ok) "QUEUED" else "SERVICE_NOT_RUNNING", if (ok) "Camsnap queued" else "Service not running")
      .withState(OpenClawForegroundService.getCachedState())
      .toJsonString()
  }

  fun requestStatus(): String {
    return UiResponse(true, "OK", "State")
      .withState(OpenClawForegroundService.getCachedState())
      .toJsonString()
  }

  fun getLastLogs(): String {
    val logs = OpenClawForegroundService.getLastLogs()
    val data = JSONObject().put("lastLogs", logs)
    return UiResponse(true, "OK", "Logs", data).toJsonString()
  }

  private const val LOG_TAG = "OPENCLAW_UI"
}

private data class UiResponse(
  val ok: Boolean,
  val code: String,
  val message: String,
  val data: JSONObject? = null,
) {
  fun withState(state: UiState): UiResponse {
    val next = data ?: JSONObject()
    next.put("state", state.toJson())
    return copy(data = next)
  }

  fun toJsonString(): String {
    val json = JSONObject()
      .put("ok", ok)
      .put("code", code)
      .put("message", message)
    if (data != null) {
      json.put("data", data)
    }
    return json.toString()
  }
}
