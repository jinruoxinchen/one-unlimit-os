package com.jinruoxinchen.oneunlimitos.memory

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinruoxinchen.oneunlimitos.accessibility.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the persistent memory and context for AI agents, including storing and
 * retrieving memories, maintaining context, and providing relevant information
 * based on user queries and system state.
 */
class MemorySystem {
    
    private val TAG = "MemorySystem"
    
    // In-memory storage for memories (would be backed by a database in production)
    private val memories = ConcurrentHashMap<String, MutableList<Memory>>()
    
    // Recent observations for short-term context
    private val recentObservations = mutableListOf<Observation>()
    private val maxRecentObservations = 50
    
    // In a real implementation, this would be a proper vector database
    // For POC, we're using a simple in-memory solution
    private val vectorStore = SimpleVectorStore()
    
    /**
     * Initialize the memory system
     */
    suspend fun initialize() {
        Log.i(TAG, "Initializing memory system")
        // In a real implementation, this would load existing memories from storage
    }
    
    /**
     * Store a new memory
     */
    suspend fun storeMemory(
        agentId: String,
        content: String,
        importance: Float = 1.0f,
        tags: List<String> = emptyList()
    ) {
        withContext(Dispatchers.IO) {
            val memory = Memory(
                id = generateMemoryId(),
                agentId = agentId,
                content = content,
                timestamp = Instant.now().epochSecond,
                importance = importance,
                tags = tags
            )
            
            // Store in memory list
            val agentMemories = memories.getOrPut(agentId) { mutableListOf() }
            agentMemories.add(memory)
            
            // Store in vector database for semantic search
            vectorStore.addMemory(memory)
            
            Log.d(TAG, "Stored memory: $content")
        }
    }
    
    /**
     * Retrieve memories relevant to a query using semantic search
     */
    suspend fun retrieveRelevantMemories(query: String, limit: Int = 5): String {
        return withContext(Dispatchers.IO) {
            try {
                val relevantMemories = vectorStore.searchMemories(query, limit)
                
                if (relevantMemories.isEmpty()) {
                    return@withContext "No relevant memories found."
                }
                
                // Format memories as a string
                relevantMemories.joinToString("\n\n") { memory ->
                    "[Memory from ${formatTimestamp(memory.timestamp)}]: ${memory.content}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving memories", e)
                "Error retrieving memories."
            }
        }
    }
    
    /**
     * Add a new observation from the system
     */
    suspend fun addObservation(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        withContext(Dispatchers.IO) {
            val observation = Observation(
                timestamp = Instant.now().epochSecond,
                eventType = event.eventType,
                packageName = event.packageName?.toString() ?: "",
                text = event.text?.joinToString(", ") ?: "",
                contentDescription = event.contentDescription?.toString() ?: ""
            )
            
            // Add to recent observations, maintaining max size
            synchronized(recentObservations) {
                recentObservations.add(0, observation)
                if (recentObservations.size > maxRecentObservations) {
                    recentObservations.removeAt(recentObservations.size - 1)
                }
            }
            
            // For significant events, store as a memory
            if (isSignificantEvent(event)) {
                val content = formatObservationAsMemory(observation)
                storeMemory("system", content, 0.7f, listOf("observation", "system_event"))
            }
        }
    }
    
    /**
     * Get recent system observations as context
     */
    fun getRecentObservationsContext(limit: Int = 10): String {
        val observations = synchronized(recentObservations) {
            recentObservations.take(limit)
        }
        
        if (observations.isEmpty()) {
            return "No recent system observations."
        }
        
        return observations.joinToString("\n") { observation ->
            "[${formatTimestamp(observation.timestamp)}] ${formatObservation(observation)}"
        }
    }
    
    /**
     * Get current UI context based on recent observations
     */
    fun getCurrentUiContext(): String {
        // In a real implementation, this would analyze recent observations
        // to build a comprehensive understanding of the current UI state
        val recentUiObservations = synchronized(recentObservations) {
            recentObservations.filter { it.eventType in UI_EVENT_TYPES }.take(5)
        }
        
        if (recentUiObservations.isEmpty()) {
            return "No recent UI activity observed."
        }
        
        return recentUiObservations.joinToString("\n") { observation ->
            "[${formatTimestamp(observation.timestamp)}] ${formatObservation(observation)}"
        }
    }
    
    /**
     * Clear all memories for testing/privacy purposes
     */
    suspend fun clearAllMemories() {
        withContext(Dispatchers.IO) {
            memories.clear()
            vectorStore.clear()
            Log.i(TAG, "Cleared all memories")
        }
    }
    
    /**
     * Helper functions
     */
    
    private fun generateMemoryId(): String {
        return "mem_${Instant.now().toEpochMilli()}_${(0..9999).random()}"
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        // Simple formatting for demo
        return Instant.ofEpochSecond(timestamp).toString()
    }
    
    private fun isSignificantEvent(event: AccessibilityEvent): Boolean {
        // Determine which events are significant enough to store as memories
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> true
            else -> false
        }
    }
    
    private fun formatObservationAsMemory(observation: Observation): String {
        // Format an observation as a memory entry
        val eventTypeStr = getEventTypeString(observation.eventType)
        
        return when (observation.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                "App changed to ${observation.packageName}"
                
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                "Notification: ${observation.text}"
                
            else -> "System event: $eventTypeStr - ${observation.text}"
        }
    }
    
    private fun formatObservation(observation: Observation): String {
        val eventTypeStr = getEventTypeString(observation.eventType)
        return "[$eventTypeStr] ${observation.packageName}: ${observation.text}"
    }
    
    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "Window Changed"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "Content Changed"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "Click"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "Focus"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "Scroll"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "Notification"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "Text Changed"
            else -> "Event $eventType"
        }
    }
    
    companion object {
        // Event types related to UI state
        private val UI_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        )
    }
}

/**
 * Data class representing a stored memory
 */
@Serializable
data class Memory(
    val id: String,
    val agentId: String,
    val content: String,
    val timestamp: Long,
    val importance: Float = 1.0f,
    val tags: List<String> = emptyList(),
    // In a real implementation, this would include embeddings
    var embedding: List<Float> = emptyList()
)

/**
 * Data class representing a system observation
 */
data class Observation(
    val timestamp: Long,
    val eventType: Int,
    val packageName: String,
    val text: String,
    val contentDescription: String
)

/**
 * Simple in-memory vector store for POC
 * In a real implementation, you would use a proper vector database
 */
class SimpleVectorStore {
    private val memories = mutableListOf<Memory>()
    
    fun addMemory(memory: Memory) {
        // In a real implementation, you would compute embeddings here
        memories.add(memory)
    }
    
    fun searchMemories(query: String, limit: Int): List<Memory> {
        // Simple search by text similarity for POC
        // In a real implementation, this would use vector similarity
        return memories.sortedByDescending { calculateSimilarity(it.content, query) }
            .take(limit)
    }
    
    fun clear() {
        memories.clear()
    }
    
    // Simple text similarity for POC
    private fun calculateSimilarity(text1: String, text2: String): Float {
        val words1 = text1.lowercase().split(Regex("\\W+")).toSet()
        val words2 = text2.lowercase().split(Regex("\\W+")).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union == 0) 0f else intersection.toFloat() / union
    }
}
