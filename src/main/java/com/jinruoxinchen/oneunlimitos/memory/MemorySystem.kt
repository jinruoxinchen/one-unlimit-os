package com.jinruoxinchen.oneunlimitos.memory

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinruoxinchen.oneunlimitos.accessibility.UiState
import com.jinruoxinchen.oneunlimitos.llm.ClaudeApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Hybrid Memory System that combines vector storage and knowledge graph for 
 * comprehensive memory management. It provides a unified interface for storing and
 * retrieving memories across different subsystems.
 */
class MemorySystem(private val llmClient: ClaudeApiClient? = null) : CoroutineScope {
    
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    
    private val TAG = "MemorySystem"
    
    // In-memory storage for memories (would be backed by a database in production)
    private val memories = ConcurrentHashMap<String, MutableList<Memory>>()
    
    // Recent observations for short-term context
    private val recentObservations = mutableListOf<Observation>()
    private val maxRecentObservations = 50
    
    // Vector database for semantic search
    private lateinit var vectorDb: VectorDatabaseMemory
    
    // Knowledge graph for relationship-based memory
    private lateinit var knowledgeGraph: KnowledgeGraphMemory
    
    // Specialized memory types
    private lateinit var userPreferencesMemory: UserPreferencesMemory
    private lateinit var appStateMemory: AppStateMemory
    private lateinit var interactionMemory: InteractionMemory
    private lateinit var deviceContextMemory: DeviceContextMemory
    
    // Memory consolidation settings
    private val consolidationThreshold = 50 // Number of memories before consolidation
    private val consolidationInterval = 3600L // Consolidate memories hourly (in seconds)
    private var lastConsolidationTime = 0L
    
    /**
     * Initialize the memory system
     */
    suspend fun initialize() {
        Log.i(TAG, "Initializing hybrid memory system")
        
        // Initialize vector database
        vectorDb = VectorDatabaseMemory(llmClient ?: ClaudeApiClient())
        vectorDb.initialize()
        
        // Initialize knowledge graph
        knowledgeGraph = KnowledgeGraphMemory()
        knowledgeGraph.initialize()
        
        // Initialize specialized memory types
        userPreferencesMemory = UserPreferencesMemory()
        userPreferencesMemory.initialize()
        
        appStateMemory = AppStateMemory()
        appStateMemory.initialize()
        
        interactionMemory = InteractionMemory()
        interactionMemory.initialize()
        
        deviceContextMemory = DeviceContextMemory()
        deviceContextMemory.initialize()
        
        // Schedule periodic memory consolidation
        scheduleMemoryConsolidation()
        
        // In a real implementation, this would load existing memories from storage
    }
    
    /**
     * Schedule periodic memory consolidation
     */
    private fun scheduleMemoryConsolidation() {
        launch {
            // Set initial consolidation time
            lastConsolidationTime = Instant.now().epochSecond
            
            // In a real implementation, this would use a proper scheduling mechanism
            // For the POC, we'll check during memory operations
        }
    }
    
    /**
     * Store a new memory
     */
    suspend fun storeMemory(
        agentId: String,
        content: String,
        importance: Float = 1.0f,
        tags: List<String> = emptyList(),
        relatedMemoryIds: List<String> = emptyList()
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
            vectorDb.addMemory(memory)
            
            // Store in knowledge graph
            knowledgeGraph.createEntityForMemory(memory)
            
            // Create relationships with related memories
            for (relatedId in relatedMemoryIds) {
                val relatedMemory = findMemoryById(relatedId)
                if (relatedMemory != null) {
                    knowledgeGraph.createRelationBetweenMemories(memory, relatedMemory, "related_to")
                }
            }
            
            // Check if consolidation is needed
            checkAndPerformConsolidation()
            
            Log.d(TAG, "Stored memory: $content")
        }
    }
    
    /**
     * Find a memory by ID
     */
    private suspend fun findMemoryById(memoryId: String): Memory? {
        return withContext(Dispatchers.IO) {
            for (agentMemories in memories.values) {
                val memory = agentMemories.find { it.id == memoryId }
                if (memory != null) {
                    return@withContext memory
                }
            }
            null
        }
    }
    
    /**
     * Retrieve memories relevant to a query using semantic search
     */
    suspend fun retrieveRelevantMemories(
        query: String,
        limit: Int = 5,
        agentId: String? = null,
        tags: List<String> = emptyList(),
        minImportance: Float = 0.0f
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Get memories from vector database
                var relevantMemories = vectorDb.searchSimilarMemories(query, limit * 2)
                
                // Filter by agent ID if specified
                if (agentId != null) {
                    relevantMemories = relevantMemories.filter { it.agentId == agentId }
                }
                
                // Filter by tags if specified
                if (tags.isNotEmpty()) {
                    relevantMemories = relevantMemories.filter { memory ->
                        memory.tags.any { it in tags }
                    }
                }
                
                // Filter by importance
                relevantMemories = relevantMemories.filter { it.importance >= minImportance }
                
                // Limit results
                relevantMemories = relevantMemories.take(limit)
                
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
     * Retrieve related memories from the knowledge graph
     */
    suspend fun retrieveRelatedMemories(memoryId: String, relationshipType: String? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                val entities = knowledgeGraph.findRelatedEntities(memoryId, relationshipType)
                
                if (entities.isEmpty()) {
                    return@withContext "No related memories found."
                }
                
                // Format entities as a string
                entities.joinToString("\n\n") { entity ->
                    "[${entity.entityType}: ${entity.name}]: ${entity.observations.joinToString("; ")}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving related memories", e)
                "Error retrieving related memories."
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
                
                // Store app state for window state changes
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val packageName = event.packageName?.toString() ?: ""
                    val className = event.className?.toString() ?: ""
                    
                    val stateData = mapOf(
                        "package_name" to packageName,
                        "class_name" to className,
                        "title" to (event.text?.joinToString(", ") ?: "")
                    )
                    
                    appStateMemory.storeAppState(
                        appPackage = packageName,
                        stateName = className.substringAfterLast('.'),
                        stateData = stateData
                    )
                }
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
    suspend fun getCurrentUiContext(): String {
        // Get the current app's package name
        val currentPackage = synchronized(recentObservations) {
            recentObservations.firstOrNull { 
                it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED 
            }?.packageName ?: ""
        }
        
        // Get the latest app state
        val appState = if (currentPackage.isNotEmpty()) {
            appStateMemory.getLatestAppState(currentPackage)
        } else {
            null
        }
        
        // In a real implementation, this would analyze recent observations
        // to build a comprehensive understanding of the current UI state
        val recentUiObservations = synchronized(recentObservations) {
            recentObservations.filter { it.eventType in UI_EVENT_TYPES }.take(5)
        }
        
        if (recentUiObservations.isEmpty() && appState == null) {
            return "No recent UI activity observed."
        }
        
        val appStateStr = appState?.let {
            "\nCurrent App: $currentPackage\nState: ${it.key}\n${it.value}"
        } ?: ""
        
        val observationsStr = recentUiObservations.joinToString("\n") { observation ->
            "[${formatTimestamp(observation.timestamp)}] ${formatObservation(observation)}"
        }
        
        return "$appStateStr\n\n$observationsStr"
    }
    
    /**
     * Clear all memories for testing/privacy purposes
     */
    suspend fun clearAllMemories() {
        withContext(Dispatchers.IO) {
            memories.clear()
            vectorDb.clear()
            
            // Clear specialized memories
            userPreferencesMemory.clear()
            appStateMemory.clear()
            interactionMemory.clear()
            deviceContextMemory.clear()
            
            Log.i(TAG, "Cleared all memories")
        }
    }
    
    /**
     * Store a user preference
     */
    suspend fun storeUserPreference(
        key: String,
        value: Any,
        category: String = "general",
        importance: Float = 1.0f
    ): Boolean {
        return userPreferencesMemory.storePreference(key, value, category, importance)
    }
    
    /**
     * Get user preferences as context
     */
    suspend fun getUserPreferencesContext(category: String? = null): String {
        val preferences = if (category != null) {
            userPreferencesMemory.getPreferencesByCategory(category)
        } else {
            userPreferencesMemory.getAllEntries()
        }
        
        if (preferences.isEmpty()) {
            return "No user preferences found."
        }
        
        return preferences.joinToString("\n") { pref ->
            "${pref.key}: ${pref.value}"
        }
    }
    
    /**
     * Store an interaction between user and agent
     */
    suspend fun storeInteraction(
        agentId: String,
        userQuery: String,
        agentResponse: String,
        success: Boolean = true,
        importance: Float = 1.0f
    ): Boolean {
        return interactionMemory.storeInteraction(
            agentId = agentId,
            userQuery = userQuery,
            agentResponse = agentResponse,
            success = success,
            importance = importance
        )
    }
    
    /**
     * Get recent interactions as context
     */
    suspend fun getRecentInteractionsContext(agentId: String? = null, limit: Int = 5): String {
        val interactions = if (agentId != null) {
            interactionMemory.getAgentInteractions(agentId, limit)
        } else {
            interactionMemory.getAllEntries().sortedByDescending { it.timestamp }.take(limit)
        }
        
        if (interactions.isEmpty()) {
            return "No recent interactions found."
        }
        
        return interactions.joinToString("\n\n") { interaction ->
            val data = interaction.value as Map<*, *>
            val query = data["user_query"]
            val response = data["agent_response"]
            
            "User: $query\nAssistant: $response"
        }
    }
    
    /**
     * Store device context information
     */
    suspend fun storeDeviceContext(
        stateType: String,
        stateData: Map<String, Any>,
        importance: Float = 1.0f
    ): Boolean {
        return deviceContextMemory.storeDeviceState(stateType, stateData, importance)
    }
    
    /**
     * Get device context information
     */
    suspend fun getDeviceContextInfo(stateType: String? = null): String {
        val deviceInfo = if (stateType != null) {
            val entry = deviceContextMemory.getDeviceState(stateType)
            if (entry != null) listOf(entry) else emptyList()
        } else {
            deviceContextMemory.getAllEntries()
        }
        
        if (deviceInfo.isEmpty()) {
            return "No device context information found."
        }
        
        return deviceInfo.joinToString("\n\n") { entry ->
            "${entry.key}:\n${entry.value}"
        }
    }
    
    /**
     * Check if memory consolidation is needed and perform it
     */
    private suspend fun checkAndPerformConsolidation() {
        val now = Instant.now().epochSecond
        val totalMemories = memories.values.sumOf { it.size }
        
        // Check if we need to consolidate based on time or memory count
        if (totalMemories > consolidationThreshold || (now - lastConsolidationTime) > consolidationInterval) {
            consolidateMemories()
            lastConsolidationTime = now
        }
    }
    
    /**
     * Consolidate memories by summarizing and pruning
     */
    private suspend fun consolidateMemories() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting memory consolidation")
                
                // For each agent, consolidate their oldest memories
                for ((agentId, agentMemories) in memories) {
                    // Skip if not enough memories to consolidate
                    if (agentMemories.size < 10) continue
                    
                    // Sort by timestamp (oldest first)
                    val sortedMemories = agentMemories.sortedBy { it.timestamp }
                    
                    // Take oldest memories for consolidation (leave recent ones untouched)
                    val memoriesToConsolidate = sortedMemories.take(sortedMemories.size / 2)
                    
                    // Group memories by tags for better summarization
                    val memoriesByTag = memoriesToConsolidate.groupBy { it.tags.firstOrNull() ?: "general" }
                    
                    for ((tag, tagMemories) in memoriesByTag) {
                        // In a real implementation, this would use the LLM to generate a summary
                        // For the POC, we'll just concatenate them
                        
                        if (tagMemories.size < 3) continue // Skip if not enough to consolidate
                        
                        val summary = "Summary of ${tagMemories.size} memories about $tag: " +
                                tagMemories.joinToString("; ") { it.content.take(50) + "..." }
                        
                        // Create a new consolidated memory
                        val consolidatedMemory = Memory(
                            id = generateMemoryId(),
                            agentId = agentId,
                            content = summary,
                            timestamp = Instant.now().epochSecond,
                            importance = tagMemories.maxOf { it.importance },
                            tags = listOf("consolidated", tag)
                        )
                        
                        // Add the consolidated memory
                        val memoryIds = tagMemories.map { it.id }
                        
                        // Remove the original memories
                        agentMemories.removeAll { it.id in memoryIds }
                        
                        // Add the consolidated memory
                        agentMemories.add(consolidatedMemory)
                        
                        // Add to vector database
                        vectorDb.addMemory(consolidatedMemory)
                        
                        // Add to knowledge graph with connections to originals
                        knowledgeGraph.createEntityForMemory(consolidatedMemory)
                        
                        Log.d(TAG, "Consolidated ${tagMemories.size} memories into one summary for agent $agentId")
                    }
                }
                
                Log.i(TAG, "Memory consolidation completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during memory consolidation", e)
            }
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
            AccessibilityEvent.TYPE_VIEW_CLICKED -> true // Add clicks as significant events
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
                
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                "User clicked on: ${observation.text}"
                
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
