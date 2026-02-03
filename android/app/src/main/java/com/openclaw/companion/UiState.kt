package com.openclaw.companion

import org.json.JSONObject

data class UiState(
  val connected: Boolean = false,
  val registered: Boolean = false,
  val lastHeartbeat: String? = null,
  val lastError: String? = null,
  val controllerUrl: String? = null,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("connected", connected)
    .put("registered", registered)
    .put("lastHeartbeat", lastHeartbeat)
    .put("lastError", lastError)
    .put("controllerUrl", controllerUrl)
}
