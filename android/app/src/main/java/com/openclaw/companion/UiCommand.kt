package com.openclaw.companion

sealed class UiCommand {
  data object Connect : UiCommand()
  data object Disconnect : UiCommand()
  data class Camsnap(val quality: Int, val maxBytes: Int) : UiCommand()
}
