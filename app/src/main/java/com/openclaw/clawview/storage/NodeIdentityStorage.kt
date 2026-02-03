package com.openclaw.clawview.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val TAG = "NodeIdentityStorage"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "node_settings")

/**
 * Persistent storage for node identity and configuration.
 * This stores the unique node ID that identifies this device in the network.
 */
class NodeIdentityStorage(private val context: Context) {

    private object PreferencesKeys {
        val NODE_ID = stringPreferencesKey("node_id")
        val SERVER_URL = stringPreferencesKey("server_url")
    }

    /**
     * Get the node ID. If none exists, generates and stores a new one.
     * Returns Flow so UI can observe changes.
     */
    val nodeIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val nodeId = preferences[PreferencesKeys.NODE_ID]
        if (nodeId.isNullOrEmpty()) {
            val newNodeId = generateNodeId()
            Log.i(TAG, "Generated new node ID: $newNodeId")
            newNodeId
        } else {
            Log.d(TAG, "Retrieved existing node ID: $nodeId")
            nodeId
        }
    }

    /**
     * Get the server URL from storage.
     */
    val serverUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SERVER_URL]
    }

    /**
     * Get the node ID synchronously. Generates new one if not exists.
     * WARNING: This blocks the thread. Use nodeIdFlow for reactive updates.
     */
    suspend fun getOrCreateNodeId(): String {
        val preferences = context.dataStore.data.first()
        return preferences[PreferencesKeys.NODE_ID] ?: run {
            val newNodeId = generateNodeId()
            saveNodeId(newNodeId)
            Log.i(TAG, "Created and saved new node ID: $newNodeId")
            newNodeId
        }
    }

    /**
     * Save node ID to persistent storage.
     */
    suspend fun saveNodeId(nodeId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NODE_ID] = nodeId
            Log.i(TAG, "Saved node ID: $nodeId")
        }
    }

    /**
     * Save server URL to persistent storage.
     */
    suspend fun saveServerUrl(serverUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = serverUrl
            Log.i(TAG, "Saved server URL: $serverUrl")
        }
    }

    /**
     * Get the server URL synchronously.
     */
    suspend fun getServerUrl(): String? {
        val preferences = context.dataStore.data.first()
        return preferences[PreferencesKeys.SERVER_URL]
    }

    /**
     * Generate a unique node ID using UUID.
     */
    private fun generateNodeId(): String {
        return "node_${UUID.randomUUID()}"
    }

    /**
     * Clear all stored data (for testing/debugging).
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
            Log.w(TAG, "Cleared all node storage")
        }
    }
}
