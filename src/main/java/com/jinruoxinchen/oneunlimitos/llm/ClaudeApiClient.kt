package com.jinruoxinchen.oneunlimitos.llm

import android.util.Log
import com.jinruoxinchen.oneunlimitos.tools.Tool
import com.jinruoxinchen.oneunlimitos.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with the Claude API to power agent intelligence.
 */
class ClaudeApiClient {
    
    private val TAG = "ClaudeApiClient"
    
    // OkHttp client for API requests
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // JSON serializer/deserializer
    private val json = Json { ignoreUnknownKeys = true }
    
    // Claude API endpoint
    private val apiEndpoint = "https://api.anthropic.com/v1/messages"
    
    // API key would be securely stored and retrieved in a real application
    // For development, we'll use environment variables or a secure configuration
    private val apiKey = System.getenv("CLAUDE_API_KEY") ?: "YOUR_CLAUDE_API_KEY" 
    
    /**
     * Sends a message to Claude and gets a response
     */
    suspend fun sendMessage(
        systemPrompt: String,
        userMessage: String,
        context: String? = null,
        tools: List<Tool>? = null,
        temperature: Double = 0.7
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Prepare message with system prompt and user message
                val messages = mutableListOf<Message>()
                
                // Add system message if provided
                if (systemPrompt.isNotEmpty()) {
                    messages.add(Message(role = "system", content = systemPrompt))
                }
                
                // Add context if provided
                val fullUserMessage = if (context != null) {
                    """
                    $context
                    
                    User request: $userMessage
                    """.trimIndent()
                } else {
                    userMessage
                }
                
                // Add user message
                messages.add(Message(role = "user", content = fullUserMessage))
                
                // Prepare API request
                val requestBody = if (tools != null && tools.isNotEmpty()) {
                    // Include tool definitions in the request
                    ClaudeRequestWithTools(
                        model = "claude-3-opus-20240229", // Use appropriate model
                        maxTokens = 4000,
                        messages = messages,
                        temperature = temperature,
                        tools = tools.map { it.getSchema() }
                    )
                } else {
                    // Standard request without tools
                    ClaudeRequest(
                        model = "claude-3-opus-20240229", // Use appropriate model
                        maxTokens = 4000,
                        messages = messages,
                        temperature = temperature
                    )
                }
                
                // Convert request to JSON
                val requestJson = when (requestBody) {
                    is ClaudeRequest -> json.encodeToString(ClaudeRequest.serializer(), requestBody)
                    is ClaudeRequestWithTools -> json.encodeToString(ClaudeRequestWithTools.serializer(), requestBody)
                    else -> throw IllegalArgumentException("Unknown request type")
                }
                
                // Build HTTP request
                val request = Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()
                
                // Execute request
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details available"
                        Log.e(TAG, "API error: ${response.code} - $errorBody")
                        throw IOException("API error: ${response.code}")
                    }
                    
                    // Parse response
                    val responseBody = response.body?.string() ?: throw IOException("Empty response")
                    val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
                    
                    // Return assistant's message content
                    claudeResponse.content.first().text
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to Claude", e)
                "I encountered an error while processing your request. Please try again later."
            }
        }
    }
    
    /**
     * Sends a message to Claude with tool calling capabilities and parses the tool use
     */
    suspend fun sendMessageWithToolCalling(
        systemPrompt: String,
        userMessage: String,
        context: String? = null,
        availableTools: List<Tool>,
        temperature: Double = 0.7
    ): ToolCallResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = sendMessage(
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    context = context,
                    tools = availableTools,
                    temperature = temperature
                )
                
                // Parse the response for tool calls
                // This is a simplified implementation
                // In a full implementation, you would use proper parsing based on Claude's response format
                
                // Example basic parsing for a tool call pattern:
                // I'll use the tool [tool_name] with parameters: [parameters]
                
                val toolCallRegex = "I'll use the tool ([a-zA-Z_]+) with parameters: (.+)".toRegex()
                val match = toolCallRegex.find(response)
                
                if (match != null) {
                    val toolName = match.groupValues[1]
                    val paramStr = match.groupValues[2]
                    
                    // Find the corresponding tool
                    val tool = availableTools.find { it.id == toolName || it.name == toolName }
                    
                    if (tool != null) {
                        ToolCallResult(
                            response = response,
                            toolCallDetected = true,
                            toolName = tool.id,
                            toolParameters = parseParameters(paramStr)
                        )
                    } else {
                        ToolCallResult(
                            response = response,
                            toolCallDetected = true,
                            toolName = toolName,
                            toolParameters = parseParameters(paramStr),
                            error = "Tool not found: $toolName"
                        )
                    }
                } else {
                    ToolCallResult(
                        response = response,
                        toolCallDetected = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message with tool calling to Claude", e)
                ToolCallResult(
                    response = "I encountered an error while processing your request. Please try again later.",
                    toolCallDetected = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Generates embedding for text using an embedding API
     * In a production implementation, you would use an embedding provider like OpenAI or HuggingFace
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        // For a POC, we'll use a simple embedding technique
        // In a real implementation, you would use a proper embedding API
        val embeddings = mutableListOf<Float>()
        
        // Create a very simple "embedding" for testing
        // This is NOT a real embedding, just a placeholder
        for (char in text.take(100)) {
            embeddings.add(char.code.toFloat() / 1000f)
        }
        
        // Pad to a fixed size for simple vector operations
        while (embeddings.size < 128) {
            embeddings.add(0f)
        }
        
        return embeddings.take(128)
    }
    
    /**
     * Helper method to parse parameters from a string
     */
    private fun parseParameters(paramStr: String): Map<String, Any> {
        // This is a simplified parameter parser
        // In a real implementation, you would use proper JSON parsing
        val params = mutableMapOf<String, Any>()
        
        val keyValuePairs = paramStr.split(", ")
        for (pair in keyValuePairs) {
            val keyValue = pair.split(": ")
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().replace("\"", "")
                val value = keyValue[1].trim().replace("\"", "")
                params[key] = value
            }
        }
        
        return params
    }
}

/**
 * Data classes for Claude API requests and responses
 */

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>,
    val temperature: Double = 0.7
)

@Serializable
data class ClaudeRequestWithTools(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val tools: List<ToolSchema>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val content: List<ContentBlock>,
    val model: String,
    val usage: Usage
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int
)

/**
 * Result from a tool call request
 */
data class ToolCallResult(
    val response: String,
    val toolCallDetected: Boolean,
    val toolName: String? = null,
    val toolParameters: Map<String, Any> = emptyMap(),
    val error: String? = null
)
