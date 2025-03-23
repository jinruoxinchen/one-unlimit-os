package com.jinruoxinchen.oneunlimitos.mcp.implementations

import android.util.Log
import com.jinruoxinchen.oneunlimitos.mcp.McpServer
import com.jinruoxinchen.oneunlimitos.memory.MemorySystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * MCP server implementation that provides access to the hybrid memory system.
 * This server exposes tools for storing and retrieving memories, managing user preferences,
 * and accessing interaction history and device context information.
 */
class MemoryMcpServer(
    private val memorySystem: MemorySystem
) : McpServer {
    
    private val TAG = "MemoryMcpServer"
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isConnected = false
    
    override val id = "memory_server"
    override val name = "Memory System"
    
    /**
     * Connect to the MCP server
     */
    override suspend fun connect(): Boolean {
        // This is a local server that's always available
        isConnected = true
        Log.i(TAG, "Connected to Memory MCP server")
        return true
    }
    
    /**
     * Disconnect from the MCP server
     */
    override suspend fun disconnect() {
        isConnected = false
        Log.i(TAG, "Disconnected from Memory MCP server")
    }
    
    /**
     * Check if the server is currently connected
     */
    override fun isConnected(): Boolean = isConnected
    
    override suspend fun listTools(): List<McpTool> {
        val toolsList = mutableListOf<McpTool>()
        
        // Store memory tool
        toolsList.add(
            McpTool(
                name = "store_memory",
                description = "Store a new memory in the system",
                inputSchema = mapOf(
                    "agent_id" to McpParameterSchema(
                        type = "string",
                        description = "ID of the agent storing the memory"
                    ),
                    "content" to McpParameterSchema(
                        type = "string",
                        description = "Content of the memory"
                    ),
                    "importance" to McpParameterSchema(
                        type = "number",
                        description = "Importance score (0.0-1.0)",
                        required = false
                    ),
                    "tags" to McpParameterSchema(
                        type = "array",
                        description = "List of tags for the memory",
                        required = false
                    ),
                    "related_memory_ids" to McpParameterSchema(
                        type = "array",
                        description = "List of related memory IDs",
                        required = false
                    )
                )
            )
        )
        // Retrieve memories tool
        toolsList.add(
            McpTool(
                name = "retrieve_memories",
                description = "Retrieve memories relevant to a query",
                inputSchema = mapOf(
                    "query" to McpParameterSchema(
                        type = "string",
                        description = "Query to search for relevant memories"
                    ),
                    "limit" to McpParameterSchema(
                        type = "integer",
                        description = "Maximum number of memories to retrieve",
                        required = false
                    ),
                    "agent_id" to McpParameterSchema(
                        type = "string",
                        description = "Filter by agent ID (optional)",
                        required = false
                    ),
                    "tags" to McpParameterSchema(
                        type = "array",
                        description = "Filter by tags (optional)",
                        required = false
                    ),
                    "min_importance" to McpParameterSchema(
                        type = "number",
                        description = "Minimum importance score (0.0-1.0)",
                        required = false
                    )
                )
            )
        )
        // Retrieve related memories tool
        toolsList.add(
            McpTool(
                name = "retrieve_related_memories",
                description = "Retrieve memories related to a given memory ID",
                inputSchema = mapOf(
                    "memory_id" to McpParameterSchema(
                        type = "string",
                        description = "ID of the memory to find related memories for"
                    ),
                    "relationship_type" to McpParameterSchema(
                        type = "string",
                        description = "Type of relationship to filter by (optional)",
                        required = false
                    )
                )
            )
        )
        // Add other tools similarly...
        toolsList.add(
            McpTool(
                name = "store_user_preference",
                description = "Store a user preference in the memory system",
                inputSchema = mapOf(
                    "key" to McpParameterSchema(
                        type = "string",
                        description = "Preference key"
                    ),
                    "value" to McpParameterSchema(
                        type = "string",
                        description = "Preference value"
                    ),
                    "category" to McpParameterSchema(
                        type = "string",
                        description = "Preference category",
                        required = false
                    ),
                    "importance" to McpParameterSchema(
                        type = "number",
                        description = "Importance score (0.0-1.0)",
                        required = false
                    )
                )
            )
        )
        toolsList.add(
            McpTool(
                name = "get_user_preferences",
                description = "Get user preferences from the memory system",
                inputSchema = mapOf(
                    "category" to McpParameterSchema(
                        type = "string",
                        description = "Filter by preference category (optional)",
                        required = false
                    )
                )
            )
        )
        toolsList.add(
            McpTool(
                name = "store_interaction",
                description = "Store an interaction between user and agent",
                inputSchema = mapOf(
                    "agent_id" to McpParameterSchema(
                        type = "string",
                        description = "ID of the agent involved in the interaction"
                    ),
                    "user_query" to McpParameterSchema(
                        type = "string",
                        description = "User's query or input"
                    ),
                    "agent_response" to McpParameterSchema(
                        type = "string",
                        description = "Agent's response"
                    ),
                    "success" to McpParameterSchema(
                        type = "boolean",
                        description = "Whether the interaction was successful",
                        required = false
                    ),
                    "importance" to McpParameterSchema(
                        type = "number",
                        description = "Importance score (0.0-1.0)",
                        required = false
                    )
                )
            )
        )
        toolsList.add(
            McpTool(
                name = "get_recent_interactions",
                description = "Get recent interactions from the memory system",
                inputSchema = mapOf(
                    "agent_id" to McpParameterSchema(
                        type = "string",
                        description = "Filter by agent ID (optional)",
                        required = false
                    ),
                    "limit" to McpParameterSchema(
                        type = "integer",
                        description = "Maximum number of interactions to retrieve",
                        required = false
                    )
                )
            )
        )
        toolsList.add(
            McpTool(
                name = "get_device_context",
                description = "Get device context information from the memory system",
                inputSchema = mapOf(
                    "state_type" to McpParameterSchema(
                        type = "string",
                        description = "Type of device state to retrieve (optional)",
                        required = false
                    )
                )
            )
        )
        toolsList.add(
            McpTool(
                name = "get_ui_context",
                description = "Get current UI context based on recent observations",
                inputSchema = mapOf()
            )
        )
        
        return toolsList
    }
    
    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): McpToolResponse {
        val result = when (toolName) {
            "store_memory" -> handleStoreMemory(parameters)
            "retrieve_memories" -> handleRetrieveMemories(parameters)
            "retrieve_related_memories" -> handleRetrieveRelatedMemories(parameters)
            "store_user_preference" -> handleStoreUserPreference(parameters)
            "get_user_preferences" -> handleGetUserPreferences(parameters)
            "store_interaction" -> handleStoreInteraction(parameters)
            "get_recent_interactions" -> handleGetRecentInteractions(parameters)
            "get_device_context" -> handleGetDeviceContext(parameters)
            "get_ui_context" -> handleGetUiContext(parameters)
            else -> mapOf(
                "error" to "Unsupported tool: $toolName"
            )
        }
        
        // Convert the result map to a McpToolResponse
        return if (result.containsKey("error")) {
            McpToolResponse(isError = true, content = result["error"] as String)
        } else {
            McpToolResponse(isError = false, content = json.encodeToString(result))
        }
    }
    
    override suspend fun listResources(): List<McpResource> {
        // Create resource templates for memory resources
        return listOf(
            McpResource(
                uri = "memory://preferences/general",
                name = "General user preferences",
                description = "Access general user preferences"
            ),
            McpResource(
                uri = "memory://interactions/system",
                name = "System interactions",
                description = "Access interaction history for the system agent"
            )
        )
    }
    
    override suspend fun readResource(uri: String): McpResourceContent {
        return try {
            // Parse the URI
            if (uri.startsWith("memory://preferences/")) {
                val category = uri.removePrefix("memory://preferences/")
                val preferencesText = memorySystem.getUserPreferencesContext(category)
                
                McpResourceContent(content = preferencesText)
            } else if (uri.startsWith("memory://interactions/")) {
                val agentId = uri.removePrefix("memory://interactions/")
                val interactionsText = memorySystem.getRecentInteractionsContext(agentId)
                
                McpResourceContent(content = interactionsText)
            } else {
                McpResourceContent(content = "Invalid memory resource URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading resource: $uri", e)
            McpResourceContent(content = "Error reading memory resource: ${e.message}")
        }
    }
    
    /**
     * Handle storing a new memory
     */
    private suspend fun handleStoreMemory(args: Map<String, Any>): Map<String, Any> {
        try {
            val agentId = args["agent_id"] as String
            val content = args["content"] as String
            val importance = (args["importance"] as? Number)?.toFloat() ?: 1.0f
            val tags = (args["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val relatedMemoryIds = (args["related_memory_ids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            
            memorySystem.storeMemory(
                agentId = agentId,
                content = content,
                importance = importance,
                tags = tags,
                relatedMemoryIds = relatedMemoryIds
            )
            
            return mapOf(
                "success" to true,
                "message" to "Memory stored successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error storing memory", e)
            return mapOf(
                "success" to false,
                "error" to "Error storing memory: ${e.message}"
            )
        }
    }
    
    /**
     * Handle retrieving memories by query
     */
    private suspend fun handleRetrieveMemories(args: Map<String, Any>): Map<String, Any> {
        try {
            val query = args["query"] as String
            val limit = (args["limit"] as? Number)?.toInt() ?: 5
            val agentId = args["agent_id"] as? String
            val tags = (args["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val minImportance = (args["min_importance"] as? Number)?.toFloat() ?: 0.0f
            
            val memories = memorySystem.retrieveRelevantMemories(
                query = query,
                limit = limit,
                agentId = agentId,
                tags = tags,
                minImportance = minImportance
            )
            
            return mapOf(
                "success" to true,
                "memories" to memories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving memories", e)
            return mapOf(
                "success" to false,
                "error" to "Error retrieving memories: ${e.message}"
            )
        }
    }
    
    /**
     * Handle retrieving related memories
     */
    private suspend fun handleRetrieveRelatedMemories(args: Map<String, Any>): Map<String, Any> {
        try {
            val memoryId = args["memory_id"] as String
            val relationshipType = args["relationship_type"] as? String
            
            val relatedMemories = memorySystem.retrieveRelatedMemories(
                memoryId = memoryId,
                relationshipType = relationshipType
            )
            
            return mapOf(
                "success" to true,
                "related_memories" to relatedMemories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving related memories", e)
            return mapOf(
                "success" to false,
                "error" to "Error retrieving related memories: ${e.message}"
            )
        }
    }
    
    /**
     * Handle storing a user preference
     */
    private suspend fun handleStoreUserPreference(args: Map<String, Any>): Map<String, Any> {
        try {
            val key = args["key"] as String
            val value = args["value"] as String
            val category = args["category"] as? String ?: "general"
            val importance = (args["importance"] as? Number)?.toFloat() ?: 1.0f
            
            val success = memorySystem.storeUserPreference(
                key = key,
                value = value,
                category = category,
                importance = importance
            )
            
            return mapOf(
                "success" to success,
                "message" to if (success) "Preference stored successfully" else "Failed to store preference"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error storing user preference", e)
            return mapOf(
                "success" to false,
                "error" to "Error storing user preference: ${e.message}"
            )
        }
    }
    
    /**
     * Handle getting user preferences
     */
    private suspend fun handleGetUserPreferences(args: Map<String, Any>): Map<String, Any> {
        try {
            val category = args["category"] as? String
            
            val preferences = memorySystem.getUserPreferencesContext(category)
            
            return mapOf(
                "success" to true,
                "preferences" to preferences
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user preferences", e)
            return mapOf(
                "success" to false,
                "error" to "Error getting user preferences: ${e.message}"
            )
        }
    }
    
    /**
     * Handle storing an interaction
     */
    private suspend fun handleStoreInteraction(args: Map<String, Any>): Map<String, Any> {
        try {
            val agentId = args["agent_id"] as String
            val userQuery = args["user_query"] as String
            val agentResponse = args["agent_response"] as String
            val success = (args["success"] as? Boolean) ?: true
            val importance = (args["importance"] as? Number)?.toFloat() ?: 1.0f
            
            val result = memorySystem.storeInteraction(
                agentId = agentId,
                userQuery = userQuery,
                agentResponse = agentResponse,
                success = success,
                importance = importance
            )
            
            return mapOf(
                "success" to result,
                "message" to if (result) "Interaction stored successfully" else "Failed to store interaction"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error storing interaction", e)
            return mapOf(
                "success" to false,
                "error" to "Error storing interaction: ${e.message}"
            )
        }
    }
    
    /**
     * Handle getting recent interactions
     */
    private suspend fun handleGetRecentInteractions(args: Map<String, Any>): Map<String, Any> {
        try {
            val agentId = args["agent_id"] as? String
            val limit = (args["limit"] as? Number)?.toInt() ?: 5
            
            val interactions = memorySystem.getRecentInteractionsContext(agentId, limit)
            
            return mapOf(
                "success" to true,
                "interactions" to interactions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent interactions", e)
            return mapOf(
                "success" to false,
                "error" to "Error getting recent interactions: ${e.message}"
            )
        }
    }
    
    /**
     * Handle getting device context
     */
    private suspend fun handleGetDeviceContext(args: Map<String, Any>): Map<String, Any> {
        try {
            val stateType = args["state_type"] as? String
            
            val deviceContext = memorySystem.getDeviceContextInfo(stateType)
            
            return mapOf(
                "success" to true,
                "device_context" to deviceContext
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device context", e)
            return mapOf(
                "success" to false,
                "error" to "Error getting device context: ${e.message}"
            )
        }
    }
    
    /**
     * Handle getting UI context
     */
    private suspend fun handleGetUiContext(args: Map<String, Any>): Map<String, Any> {
        try {
            val uiContext = memorySystem.getCurrentUiContext()
            
            return mapOf(
                "success" to true,
                "ui_context" to uiContext
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UI context", e)
            return mapOf(
                "success" to false,
                "error" to "Error getting UI context: ${e.message}"
            )
        }
    }
}
