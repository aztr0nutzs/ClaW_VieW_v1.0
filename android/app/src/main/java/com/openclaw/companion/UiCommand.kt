package com.openclaw.companion

sealed class UiCommand {
  data class Connect(val controllerUrl: String?) : UiCommand()
  data object Disconnect : UiCommand()
  data class Camsnap(val quality: Int, val maxBytes: Int) : UiCommand()
}
