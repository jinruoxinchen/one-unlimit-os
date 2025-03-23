package com.jinruoxinchen.oneunlimitos.memory

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for specialized memory types that provide focused data storage and retrieval
 * for specific aspects of the system's operation.
 */
abstract class SpecializedMemory(val memoryType: String) {
    
    protected val TAG = "SpecializedMemory:$memoryType"
    
    // Storage for specialized memory entries
    protected val entries = ConcurrentHashMap<String, MemoryEntry>()
    
    /**
     * Initialize the specialized memory
     */
    open suspend fun initialize() {
        Log.i(TAG, "Initializing $memoryType specialized memory")
    }
    
    /**
     * Store a new memory entry
     */
    suspend fun storeEntry(entry: MemoryEntry): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                entries[entry.key] = entry
                Log.d(TAG, "Stored $memoryType entry: ${entry.key}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error storing $memoryType entry", e)
                false
            }
        }
    }
    
    /**
     * Retrieve a memory entry by key
     */
    suspend fun getEntry(key: String): MemoryEntry? {
        return withContext(Dispatchers.IO) {
            entries[key]
        }
    }
    
    /**
     * Retrieve all memory entries
     */
    suspend fun getAllEntries(): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.toList()
        }
    }
    
    /**
     * Search for entries matching a query
     */
    suspend fun searchEntries(query: String): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.key.contains(query, ignoreCase = true) ||
                entry.value.toString().contains(query, ignoreCase = true)
            }.toList()
        }
    }
    
    /**
     * Clear all entries
     */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            entries.clear()
            Log.i(TAG, "Cleared all $memoryType entries")
        }
    }
    
    /**
     * Convert a specialized memory entry to a general Memory
     */
    fun convertToMemory(entry: MemoryEntry): Memory {
        return Memory(
            id = "${memoryType}_${entry.key}",
            agentId = "system",
            content = "${entry.key}: ${entry.value}",
            timestamp = entry.timestamp,
            importance = entry.importance,
            tags = listOf(memoryType) + entry.tags
        )
    }
}

/**
 * User Preferences Memory stores user preferences and patterns
 */
class UserPreferencesMemory : SpecializedMemory("user_preferences") {
    
    /**
     * Store a user preference
     */
    suspend fun storePreference(
        key: String,
        value: Any,
        category: String = "general",
        importance: Float = 1.0f
    ): Boolean {
        val entry = MemoryEntry(
            key = key,
            value = value,
            timestamp = Instant.now().epochSecond,
            importance = importance,
            tags = listOf("preference", category)
        )
        
        return storeEntry(entry)
    }
    
    /**
     * Get preferences by category
     */
    suspend fun getPreferencesByCategory(category: String): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.tags.contains(category)
            }.toList()
        }
    }
    
    /**
     * Check if a preference exists
     */
    fun hasPreference(key: String): Boolean {
        return entries.containsKey(key)
    }
}

/**
 * App State Memory remembers navigation paths and app states
 */
class AppStateMemory : SpecializedMemory("app_state") {
    
    /**
     * Store an app state
     */
    suspend fun storeAppState(
        appPackage: String,
        stateName: String,
        stateData: Map<String, Any>,
        importance: Float = 1.0f
    ): Boolean {
        val key = "$appPackage:$stateName"
        val entry = MemoryEntry(
            key = key,
            value = stateData,
            timestamp = Instant.now().epochSecond,
            importance = importance,
            tags = listOf("app_state", appPackage)
        )
        
        return storeEntry(entry)
    }
    
    /**
     * Get states for a specific app
     */
    suspend fun getAppStates(appPackage: String): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.tags.contains(appPackage)
            }.toList()
        }
    }
    
    /**
     * Get the most recent state for an app
     */
    suspend fun getLatestAppState(appPackage: String): MemoryEntry? {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.tags.contains(appPackage)
            }.maxByOrNull { it.timestamp }
        }
    }
}

/**
 * Interaction Memory records user-agent interactions
 */
class InteractionMemory : SpecializedMemory("interaction") {
    
    /**
     * Store an interaction
     */
    suspend fun storeInteraction(
        agentId: String,
        userQuery: String,
        agentResponse: String,
        success: Boolean = true,
        importance: Float = 1.0f
    ): Boolean {
        val key = "interaction_${Instant.now().toEpochMilli()}"
        val interactionData = mapOf(
            "agent_id" to agentId,
            "user_query" to userQuery,
            "agent_response" to agentResponse,
            "success" to success
        )
        
        val entry = MemoryEntry(
            key = key,
            value = interactionData,
            timestamp = Instant.now().epochSecond,
            importance = importance,
            tags = listOf("interaction", agentId, if (success) "success" else "failure")
        )
        
        return storeEntry(entry)
    }
    
    /**
     * Get interactions for a specific agent
     */
    suspend fun getAgentInteractions(agentId: String, limit: Int = 10): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.tags.contains(agentId)
            }.sortedByDescending { it.timestamp }.take(limit)
        }
    }
    
    /**
     * Get successful or failed interactions
     */
    suspend fun getInteractionsByResult(success: Boolean, limit: Int = 10): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            val tag = if (success) "success" else "failure"
            entries.values.filter { entry ->
                entry.tags.contains(tag)
            }.sortedByDescending { it.timestamp }.take(limit)
        }
    }
}

/**
 * Device Context Memory tracks device state, installed apps, and system status
 */
class DeviceContextMemory : SpecializedMemory("device_context") {
    
    /**
     * Store device state
     */
    suspend fun storeDeviceState(
        stateType: String,
        stateData: Map<String, Any>,
        importance: Float = 1.0f
    ): Boolean {
        val key = "device_state_$stateType"
        val entry = MemoryEntry(
            key = key,
            value = stateData,
            timestamp = Instant.now().epochSecond,
            importance = importance,
            tags = listOf("device_state", stateType)
        )
        
        return storeEntry(entry)
    }
    
    /**
     * Store installed app information
     */
    suspend fun storeAppInfo(
        packageName: String,
        appInfo: Map<String, Any>,
        importance: Float = 0.7f
    ): Boolean {
        val key = "app_info_$packageName"
        val entry = MemoryEntry(
            key = key,
            value = appInfo,
            timestamp = Instant.now().epochSecond,
            importance = importance,
            tags = listOf("app_info", packageName)
        )
        
        return storeEntry(entry)
    }
    
    /**
     * Get device state by type
     */
    suspend fun getDeviceState(stateType: String): MemoryEntry? {
        return withContext(Dispatchers.IO) {
            entries.values.find { entry ->
                entry.key == "device_state_$stateType"
            }
        }
    }
    
    /**
     * Get app info for a specific package
     */
    suspend fun getAppInfo(packageName: String): MemoryEntry? {
        return withContext(Dispatchers.IO) {
            entries.values.find { entry ->
                entry.key == "app_info_$packageName"
            }
        }
    }
    
    /**
     * Get all installed app info
     */
    suspend fun getAllAppInfo(): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            entries.values.filter { entry ->
                entry.tags.contains("app_info")
            }.toList()
        }
    }
}

/**
 * Data class representing a specialized memory entry
 */
@Serializable
data class MemoryEntry(
    val key: String,
    val value: Any,
    val timestamp: Long,
    val importance: Float = 1.0f,
    val tags: List<String> = emptyList()
)
