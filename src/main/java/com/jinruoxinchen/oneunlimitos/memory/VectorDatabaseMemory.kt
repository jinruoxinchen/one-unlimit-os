package com.jinruoxinchen.oneunlimitos.memory

import android.util.Log
import com.jinruoxinchen.oneunlimitos.llm.ClaudeApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.PriorityQueue
import kotlin.math.sqrt

/**
 * Vector Database Memory component that stores and retrieves memory embeddings.
 * This implementation is an in-memory store for the POC, but would be replaced
 * with a proper vector database like Chroma or FAISS in a production implementation.
 */
class VectorDatabaseMemory(private val llmClient: ClaudeApiClient) {
    
    private val TAG = "VectorDatabaseMemory"
    
    // In-memory storage for memory embeddings
    private val memoryEmbeddings = mutableListOf<MemoryEmbedding>()
    
    // Dimension of the embedding vectors
    private val embeddingDimension = 128 // Would be larger in a production implementation
    
    /**
     * Initialize the vector database memory
     */
    suspend fun initialize() {
        Log.i(TAG, "Initializing vector database memory")
    }
    
    /**
     * Add a memory to the vector database
     */
    suspend fun addMemory(memory: Memory): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                // Generate embedding for the memory content
                val embedding = llmClient.generateEmbedding(memory.content)
                
                // In a real implementation, this would be stored in a proper vector database
                val memoryEmbedding = MemoryEmbedding(
                    memoryId = memory.id,
                    embedding = embedding,
                    memory = memory
                )
                
                memoryEmbeddings.add(memoryEmbedding)
                Log.d(TAG, "Added memory embedding for: ${memory.id}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding memory to vector database", e)
                false
            }
        }
    }
    
    /**
     * Search for memories similar to the query
     */
    suspend fun searchSimilarMemories(query: String, limit: Int = 5): List<Memory> {
        return withContext(Dispatchers.Default) {
            try {
                // Generate embedding for the query
                val queryEmbedding = llmClient.generateEmbedding(query)
                
                // Find memories with similar embeddings using cosine similarity
                // In a real implementation, this would use efficient nearest neighbor search
                
                // Priority queue to keep top matches
                val topMatches = PriorityQueue<Pair<Double, Memory>>(compareBy { -it.first })
                
                for (memoryEmbedding in memoryEmbeddings) {
                    val similarity = cosineSimilarity(queryEmbedding, memoryEmbedding.embedding)
                    topMatches.add(Pair(similarity, memoryEmbedding.memory))
                    
                    // Keep only the top 'limit' items
                    while (topMatches.size > limit) {
                        topMatches.poll()
                    }
                }
                
                // Extract memories from the priority queue
                val result = mutableListOf<Memory>()
                while (topMatches.isNotEmpty()) {
                    result.add(0, topMatches.poll().second) // Add at the beginning to reverse order
                }
                
                Log.d(TAG, "Found ${result.size} similar memories for query")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error searching similar memories", e)
                emptyList()
            }
        }
    }
    
    /**
     * Clear all memories from the vector database
     */
    fun clear() {
        memoryEmbeddings.clear()
        Log.i(TAG, "Cleared all memories from vector database")
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     */
    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Double {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0
        
        val dimension = minOf(vec1.size, vec2.size)
        
        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0
        
        for (i in 0 until dimension) {
            dotProduct += vec1[i] * vec2[i]
            magnitude1 += vec1[i] * vec1[i]
            magnitude2 += vec2[i] * vec2[i]
        }
        
        if (magnitude1 <= 0.0 || magnitude2 <= 0.0) return 0.0
        
        return dotProduct / (sqrt(magnitude1) * sqrt(magnitude2))
    }
    
    /**
     * Find memories by agent ID
     */
    fun findMemoriesByAgent(agentId: String): List<Memory> {
        return memoryEmbeddings.filter { it.memory.agentId == agentId }
            .map { it.memory }
    }
    
    /**
     * Find memories by tags
     */
    fun findMemoriesByTags(tags: List<String>): List<Memory> {
        return memoryEmbeddings.filter { memoryEmbedding ->
            memoryEmbedding.memory.tags.any { it in tags }
        }.map { it.memory }
    }
    
    /**
     * Get the number of memories stored
     */
    fun getMemoryCount(): Int {
        return memoryEmbeddings.size
    }
}

/**
 * Data class representing a memory embedding
 */
data class MemoryEmbedding(
    val memoryId: String,
    val embedding: List<Float>,
    val memory: Memory
)
