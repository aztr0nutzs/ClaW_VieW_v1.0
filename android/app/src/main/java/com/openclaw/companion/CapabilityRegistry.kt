package com.openclaw.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class CapabilityDefinition(
  val name: String,
  val version: Int,
  val requiredPermissions: List<String>,
)

object CapabilityRegistry {
  private val capabilities = listOf(
    CapabilityDefinition(
      name = "camsnap",
      version = 1,
      requiredPermissions = listOf(Manifest.permission.CAMERA),
    ),
  )

  fun manifest(): JSONArray {
    val array = JSONArray()
    capabilities.forEach { capability ->
      array.put(
        JSONObject()
          .put("name", capability.name)
          .put("version", capability.version),
      )
    }
    return array
  }

  fun get(name: String): CapabilityDefinition? = capabilities.firstOrNull { it.name == name }

  fun missingPermissions(context: Context, capability: CapabilityDefinition): List<String> {
    return capability.requiredPermissions.filter { permission ->
      ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }
  }
}
