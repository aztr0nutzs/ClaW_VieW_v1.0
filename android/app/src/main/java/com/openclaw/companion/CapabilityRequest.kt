package com.openclaw.companion

import org.json.JSONObject

data class CapabilityRequest(
  val requestId: String,
  val capability: String,
  val args: JSONObject,
)
