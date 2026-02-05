package com.openclaw.companion

import android.content.Context

object ControllerConfig {
  private const val PREFS_NAME = "openclaw_prefs"
  private const val KEY_CONTROLLER_URL = "controller_url"

  fun getControllerUrl(context: Context): String? {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CONTROLLER_URL, null)
  }

  fun setControllerUrl(context: Context, url: String) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_CONTROLLER_URL, url).apply()
  }
}
