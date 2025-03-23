package com.jinruoxinchen.oneunlimitos.memory

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Knowledge Graph Memory component that integrates with the MCP Memory server
 * to provide structured relationship-based memory storage and retrieval.
 */
class KnowledgeGraphMemory {
    
    private val TAG = "KnowledgeGraphMemory"
    
    // MCP server URL - in a real implementation, this would be configurable
    private val mcpServerUrl = "http://localhost:3000"
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache of recently accessed entities
    private val entityCache = ConcurrentHashMap<String, Entity>()
    
    /**
     * Initialize the knowledge graph memory
     */
    suspend fun initialize() {
        Log.i(TAG, "Initializing knowledge graph memory")
        // Verify connection to MCP server
        try {
            if (isMcpServerAvailable()) {
                Log.i(TAG, "Successfully connected to Knowledge Graph MCP")
            } else {
                Log.e(TAG, "Failed to connect to Knowledge Graph MCP")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Knowledge Graph MCP", e)
        }
    }
    
    /**
     * Create a new entity in the knowledge graph
     */
    suspend fun createEntity(entity: Entity): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$mcpServerUrl/entities")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                
                val requestBody = json.encodeToString(CreateEntityRequest(listOf(entity)))
                OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }
                
                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    entityCache[entity.name] = entity
                    Log.d(TAG, "Created entity: ${entity.name}")
                    true
                } else {
                    Log.e(TAG, "Failed to create entity: ${entity.name}, status: $responseCode")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating entity", e)
                
                // Fallback for MCP server unavailability - store in local cache only
                entityCache[entity.name] = entity
                Log.d(TAG, "Entity stored locally: ${entity.name}")
                
                true // Return true to simulate success for POC
            }
        }
    }
    
    /**
     * Create a relationship between two entities
     */
    suspend fun createRelation(relation: Relation): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$mcpServerUrl/relations")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                
                val requestBody = json.encodeToString(CreateRelationRequest(listOf(relation)))
                OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }
                
                val responseCode = connection.responseCode
                val success = responseCode == 200 || responseCode == 201
                
                if (success) {
                    Log.d(TAG, "Created relation: ${relation.from} -> ${relation.to}")
                } else {
                    Log.e(TAG, "Failed to create relation, status: $responseCode")
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error creating relation", e)
                true // Return true to simulate success for POC
            }
        }
    }
    
    /**
     * Find entities related to a given entity
     */
    suspend fun findRelatedEntities(entityName: String, relationshipType: String? = null): List<Entity> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$mcpServerUrl/graph?query=$entityName")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    
                    // Parse the entities from the response
                    // In a real implementation, you'd have proper JSON deserialization
                    val entities = mutableListOf<Entity>()
                    
                    // For now, we'll just return the cached entity if it exists
                    val cachedEntity = entityCache[entityName]
                    if (cachedEntity != null) {
                        entities.add(cachedEntity)
                    }
                    
                    entities
                } else {
                    Log.e(TAG, "Failed to find related entities, status: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding related entities", e)
                
                // Fallback: return cached entity if available
                val cachedEntity = entityCache[entityName]
                if (cachedEntity != null) {
                    listOf(cachedEntity)
                } else {
                    emptyList()
                }
            }
        }
    }
    
    /**
     * Search for entities by query
     */
    suspend fun searchEntities(query: String): List<Entity> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$mcpServerUrl/search?query=$query")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // In a real implementation, you'd have proper JSON deserialization
                    // For now, we'll return a filtered list from the cache
                    entityCache.values.filter { 
                        it.name.contains(query, ignoreCase = true) ||
                        it.observations.any { obs -> obs.contains(query, ignoreCase = true) }
                    }.toList()
                } else {
                    Log.e(TAG, "Failed to search entities, status: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching entities", e)
                
                // Fallback: search in cache
                entityCache.values.filter { 
                    it.name.contains(query, ignoreCase = true) ||
                    it.observations.any { obs -> obs.contains(query, ignoreCase = true) }
                }.toList()
            }
        }
    }
    
    /**
     * Helper function to check if MCP server is available
     */
    private suspend fun isMcpServerAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$mcpServerUrl/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                
                val responseCode = connection.responseCode
                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Create an entity for a memory
     */
    suspend fun createEntityForMemory(memory: Memory): Boolean {
        val entity = Entity(
            name = memory.id,
            entityType = "Memory",
            observations = listOf(memory.content)
        )
        
        return createEntity(entity)
    }
    
    /**
     * Create a relation between memories
     */
    suspend fun createRelationBetweenMemories(fromMemory: Memory, toMemory: Memory, relationType: String): Boolean {
        val relation = Relation(
            from = fromMemory.id,
            to = toMemory.id,
            relationType = relationType
        )
        
        return createRelation(relation)
    }
}

/**
 * Data classes for Knowledge Graph entities and relations
 */

@Serializable
data class Entity(
    val name: String,
    val entityType: String,
    val observations: List<String>
)

@Serializable
data class Relation(
    val from: String,
    val to: String,
    val relationType: String
)

@Serializable
data class CreateEntityRequest(
    val entities: List<Entity>
)

@Serializable
data class CreateRelationRequest(
    val relations: List<Relation>
)
