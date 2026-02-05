package com.openclaw.companion

import android.content.Context
import android.util.Log
import java.util.UUID

object NodeIdentity {
  private const val PREFS_NAME = "openclaw_prefs"
  private const val KEY_NODE_ID = "node_id"

  @Synchronized
  fun getOrCreate(context: Context): String {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_NODE_ID, null)
    if (!existing.isNullOrBlank()) {
      Log.i("OPENCLAW_NODE", "NODE_ID_LOADED $existing")
      return existing
    }
    val nodeId = UUID.randomUUID().toString()
    prefs.edit().putString(KEY_NODE_ID, nodeId).apply()
    Log.i("OPENCLAW_NODE", "NODE_ID_CREATED $nodeId")
    return nodeId
  }
}
