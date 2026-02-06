package com.openclaw.companion

sealed class UiCommand {
  data class Connect(val controllerUrl: String? = null) : UiCommand()
  data object Disconnect : UiCommand()
  data class Camsnap(val quality: Int, val maxBytes: Int) : UiCommand()
  data class CameraPermission(val granted: Boolean) : UiCommand()
}
