package com.openclaw.companion

import org.json.JSONArray
import org.json.JSONObject

enum class RegistrationState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED_UNREGISTERED,
  REGISTERING,
  REGISTERED,
  ERROR,
}

data class GatewayError(
  val code: String,
  val message: String,
)

data class Capability(
  val name: String,
  val version: Int,
)

sealed class GatewayMessage {
  abstract val version: Int
  abstract fun toJson(): JSONObject

  data class Register(
    override val version: Int = 1,
    val nodeId: String,
    val capabilities: List<Capability>,
  ) : GatewayMessage() {
    override fun toJson(): JSONObject = JSONObject()
      .put("type", "register")
      .put("version", version)
      .put("nodeId", nodeId)
      .put("capabilities", JSONArray(capabilities.map { cap ->
        JSONObject()
          .put("name", cap.name)
          .put("version", cap.version)
      }))
  }

  data class RegisterAck(
    override val version: Int = 1,
    val ok: Boolean,
    val error: GatewayError? = null,
  ) : GatewayMessage() {
    override fun toJson(): JSONObject = JSONObject()
      .put("type", "register_ack")
      .put("version", version)
      .put("ok", ok)
      .put("error", error?.let { JSONObject().put("code", it.code).put("message", it.message) })
  }

  data class Error(
    override val version: Int = 1,
    val code: String,
    val message: String,
  ) : GatewayMessage() {
    override fun toJson(): JSONObject = JSONObject()
      .put("type", "error")
      .put("version", version)
      .put("code", code)
      .put("message", message)
  }

  companion object {
    fun fromJson(json: JSONObject): GatewayMessage? {
      return when (json.optString("type")) {
        "register" -> {
          val caps = json.optJSONArray("capabilities") ?: JSONArray()
          val list = (0 until caps.length()).mapNotNull { idx ->
            caps.optJSONObject(idx)?.let {
              Capability(
                name = it.optString("name"),
                version = it.optInt("version", 1),
              )
            }
          }
          Register(
            version = json.optInt("version", 1),
            nodeId = json.optString("nodeId"),
            capabilities = list,
          )
        }
        "register_ack" -> {
          val errObj = json.optJSONObject("error")
          val err = errObj?.let { GatewayError(it.optString("code"), it.optString("message")) }
          RegisterAck(
            version = json.optInt("version", 1),
            ok = json.optBoolean("ok", false),
            error = err,
          )
        }
        "error" -> {
          Error(
            version = json.optInt("version", 1),
            code = json.optString("code"),
            message = json.optString("message"),
          )
        }
        else -> null
      }
    }
  }
}
