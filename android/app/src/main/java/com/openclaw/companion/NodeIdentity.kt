package com.openclaw.companion

import android.content.Context
import java.util.UUID

object NodeIdentity {
  private const val PREFS_NAME = "openclaw_prefs"
  private const val KEY_NODE_ID = "node_id"

  fun getOrCreate(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_NODE_ID, null)
    if (!existing.isNullOrBlank()) {
      return existing
    }
    val created = UUID.randomUUID().toString()
    prefs.edit().putString(KEY_NODE_ID, created).apply()
    return created
  }
}
