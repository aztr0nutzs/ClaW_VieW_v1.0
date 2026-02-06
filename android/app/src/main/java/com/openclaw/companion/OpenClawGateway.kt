package com.openclaw.companion

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

class OpenClawGateway(
  private val context: Context,
  private val onState: (connected: Boolean, registered: Boolean, lastError: String?) -> Unit,
  private val onLog: (String) -> Unit,
  private val onProtocolLog: (String) -> Unit,
  private val onHeartbeat: (String) -> Unit,
  private val onCapabilityRequest: (requestId: String?, capability: String, args: JSONObject) -> Unit,
  private val getNodeId: () -> String,
  private val getCapabilities: () -> JSONArray,
) {
  private val client = GatewayClient(
    onState = onState,
    onLog = onLog,
    onProtocolLog = onProtocolLog,
    onHeartbeat = onHeartbeat,
    onCapabilityRequest = onCapabilityRequest,
    getNodeId = getNodeId,
    getCapabilities = getCapabilities,
    getPlatform = { "android" },
    getAppVersion = { appVersion() },
    getDeviceInfo = { deviceInfo() },
  )

  fun connect(url: String) = client.connect(url)

  fun disconnect() = client.disconnect()

  fun shutdown() = client.shutdown()

  fun sendCapabilityResult(result: CapabilityResult): Boolean = client.sendCapabilityResult(result)

  private fun appVersion(): String? {
    return runCatching {
      val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      pkgInfo.versionName
    }.getOrNull()
  }

  private fun deviceInfo(): JSONObject {
    return JSONObject()
      .put("model", Build.MODEL)
      .put("manufacturer", Build.MANUFACTURER)
      .put("sdkInt", Build.VERSION.SDK_INT)
  }
}
