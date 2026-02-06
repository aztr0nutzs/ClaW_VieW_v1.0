package com.openclaw.companion

import org.json.JSONObject

data class CapabilityResult(
  val requestId: String?,
  val capability: String,
  val ok: Boolean,
  val code: String,
  val message: String,
  val data: JSONObject? = null,
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
      .put("requestId", requestId)
      .put("capability", capability)
      .put("ok", ok)
      .put("code", code)
      .put("message", message)
    if (data != null) {
      json.put("data", data)
    }
    return json
  }
}
