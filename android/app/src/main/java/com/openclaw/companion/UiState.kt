package com.openclaw.companion

import org.json.JSONObject

data class UiState(
  val nodeId: String? = null,
  val connected: Boolean = false,
  val registered: Boolean = false,
  val sessionId: String? = null,
  val lastHeartbeat: String? = null,
  val lastError: String? = null,
  val controllerUrl: String? = null,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("nodeId", nodeId)
    .put("connected", connected)
    .put("registered", registered)
    .put("sessionId", sessionId)
    .put("lastHeartbeat", lastHeartbeat)
    .put("lastError", lastError)
    .put("controllerUrl", controllerUrl)
}
