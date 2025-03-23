package com.jinruoxinchen.oneunlimitos.mcp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Represents an MCP server connection that provides additional tools and resources
 * to extend the capabilities of the One Unlimited OS.
 */
interface McpServer {
    /**
     * The unique identifier for this MCP server
     */
    val id: String
    
    /**
     * The human-readable name of this MCP server
     */
    val name: String
    
    /**
     * Connect to the MCP server
     */
    suspend fun connect(): Boolean
    
    /**
     * Disconnect from the MCP server
     */
    suspend fun disconnect()
    
    /**
     * Check if the server is currently connected
     */
    fun isConnected(): Boolean
    
    /**
     * Get the list of tools provided by this MCP server
     */
    suspend fun listTools(): List<McpTool>
    
    /**
     * Execute a tool with the given parameters
     */
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): McpToolResponse
    
    /**
     * Get the list of resources provided by this MCP server
     */
    suspend fun listResources(): List<McpResource>
    
    /**
     * Read a resource by its URI
     */
    suspend fun readResource(uri: String): McpResourceContent
}

/**
 * Base implementation of an MCP server using HTTP
 */
abstract class HttpMcpServer(
    override val id: String,
    override val name: String,
    protected val baseUrl: String,
    protected val apiKey: String? = null
) : McpServer {
    
    private val TAG = "HttpMcpServer"
    private var connected = false
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                apiKey?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                
                val responseCode = connection.responseCode
                connected = responseCode == 200
                
                if (connected) {
                    Log.i(TAG, "Connected to MCP server: $name ($baseUrl)")
                } else {
                    Log.e(TAG, "Failed to connect to MCP server: $name, status code: $responseCode")
                }
                
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to MCP server: $name", e)
                connected = false
                false
            }
        }
    }
    
    override suspend fun disconnect() {
        connected = false
        Log.i(TAG, "Disconnected from MCP server: $name")
    }
    
    override fun isConnected(): Boolean = connected
    
    override suspend fun listTools(): List<McpTool> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/tools")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                apiKey?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val toolsResponse = json.decodeFromString<McpToolsResponse>(response)
                    toolsResponse.tools
                } else {
                    Log.e(TAG, "Failed to list tools, status code: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing tools", e)
                emptyList()
            }
        }
    }
    
    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/tools/$toolName/execute")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                apiKey?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                
                val requestBody = json.encodeToString(McpToolRequest(parameters))
                connection.outputStream.use { it.write(requestBody.toByteArray()) }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    json.decodeFromString<McpToolResponse>(response)
                } else {
                    Log.e(TAG, "Failed to execute tool $toolName, status code: $responseCode")
                    McpToolResponse(isError = true, content = "Failed to execute tool: $toolName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool $toolName", e)
                McpToolResponse(isError = true, content = "Error executing tool: ${e.message}")
            }
        }
    }
    
    override suspend fun listResources(): List<McpResource> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/resources")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                apiKey?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val resourcesResponse = json.decodeFromString<McpResourcesResponse>(response)
                    resourcesResponse.resources
                } else {
                    Log.e(TAG, "Failed to list resources, status code: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing resources", e)
                emptyList()
            }
        }
    }
    
    override suspend fun readResource(uri: String): McpResourceContent {
        return withContext(Dispatchers.IO) {
            try {
                val encodedUri = uri.replace("/", "%2F").replace(":", "%3A")
                val url = URL("$baseUrl/resources?uri=$encodedUri")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                apiKey?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    json.decodeFromString<McpResourceContent>(response)
                } else {
                    Log.e(TAG, "Failed to read resource $uri, status code: $responseCode")
                    McpResourceContent(content = "Failed to read resource: $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading resource $uri", e)
                McpResourceContent(content = "Error reading resource: ${e.message}")
            }
        }
    }
}

/**
 * Data class representing an MCP tool
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, McpParameterSchema>
)

/**
 * Data class representing an MCP parameter schema
 */
@Serializable
data class McpParameterSchema(
    val type: String,
    val description: String,
    val required: Boolean = true
)

/**
 * Data class representing an MCP tool request
 */
@Serializable
data class McpToolRequest(
    @SerialName("parameters")
    val parameters: Map<String, Any>
)

/**
 * Data class representing an MCP tool response
 */
@Serializable
data class McpToolResponse(
    val isError: Boolean = false,
    val content: String
)

/**
 * Data class representing an MCP resource
 */
@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null
)

/**
 * Data class representing an MCP resource content
 */
@Serializable
data class McpResourceContent(
    val content: String
)

/**
 * Data class representing an MCP tools response
 */
@Serializable
data class McpToolsResponse(
    val tools: List<McpTool>
)

/**
 * Data class representing an MCP resources response
 */
@Serializable
data class McpResourcesResponse(
    val resources: List<McpResource>
)
