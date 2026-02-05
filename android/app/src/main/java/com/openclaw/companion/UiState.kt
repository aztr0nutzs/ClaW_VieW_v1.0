package com.openclaw.companion

import org.json.JSONObject

data class UiState(
  val nodeId: String? = null,
  val connected: Boolean = false,
  val registered: Boolean = false,
  val registrationState: String = "DISCONNECTED",
  val lastHeartbeat: String? = null,
  val lastErrorCode: String? = null,
  val lastErrorMessage: String? = null,
  val controllerUrl: String? = null,
  val gatewayUrl: String? = null,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("nodeId", nodeId)
    .put("connected", connected)
    .put("registered", registered)
    .put("registrationState", registrationState)
    .put("lastHeartbeat", lastHeartbeat)
    .put("lastError", JSONObject()
      .put("code", lastErrorCode)
      .put("message", lastErrorMessage))
    .put("controllerUrl", controllerUrl)
    .put("gatewayUrl", gatewayUrl)
}
