package com.jinruoxinchen.oneunlimitos.mcp

import android.util.Log
import com.jinruoxinchen.oneunlimitos.tools.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Manages all MCP server connections and provides a central interface for 
 * interacting with MCP servers.
 */
class McpServerManager : CoroutineScope {
    
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    
    private val TAG = "McpServerManager"
    
    // Singleton instance
    companion object {
        @Volatile
        private var instance: McpServerManager? = null
        
        fun getInstance(): McpServerManager {
            return instance ?: synchronized(this) {
                instance ?: McpServerManager().also { instance = it }
            }
        }
    }
    
    // Map of server ID to server implementation
    private val servers = mutableMapOf<String, McpServer>()
    
    // Connection state for all servers
    private val _connectionState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connectionState: StateFlow<Map<String, Boolean>> = _connectionState.asStateFlow()
    
    // Server registry state
    private val _registeredServers = MutableStateFlow<List<McpServerInfo>>(emptyList())
    val registeredServers: StateFlow<List<McpServerInfo>> = _registeredServers.asStateFlow()
    
    /**
     * Initialize the MCP server manager
     */
    fun initialize() {
        Log.i(TAG, "Initializing MCP server manager")
    }
    
    /**
     * Register an MCP server
     */
    fun registerServer(server: McpServer) {
        servers[server.id] = server
        _registeredServers.value = _registeredServers.value + McpServerInfo(
            id = server.id,
            name = server.name,
            isConnected = server.isConnected()
        )
        
        updateConnectionState(server.id, server.isConnected())
        
        Log.i(TAG, "Registered MCP server: ${server.id} (${server.name})")
    }
    
    /**
     * Get an MCP server by ID
     */
    fun getServer(serverId: String): McpServer? {
        return servers[serverId]
    }
    
    /**
     * Connect to all registered MCP servers
     */
    suspend fun connectAllServers() {
        withContext(Dispatchers.IO) {
            servers.values.forEach { server ->
                launch {
                    try {
                        val connected = server.connect()
                        updateConnectionState(server.id, connected)
                        Log.i(TAG, "Connection to MCP server ${server.id}: ${if (connected) "success" else "failed"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to MCP server: ${server.id}", e)
                        updateConnectionState(server.id, false)
                    }
                }
            }
        }
    }
    
    /**
     * Connect to a specific MCP server
     */
    suspend fun connectServer(serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val server = servers[serverId] ?: return@withContext false
            
            try {
                val connected = server.connect()
                updateConnectionState(serverId, connected)
                
                Log.i(TAG, "Connection to MCP server $serverId: ${if (connected) "success" else "failed"}")
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to MCP server: $serverId", e)
                updateConnectionState(serverId, false)
                false
            }
        }
    }
    
    /**
     * Disconnect from all MCP servers
     */
    suspend fun disconnectAllServers() {
        withContext(Dispatchers.IO) {
            servers.values.forEach { server ->
                launch {
                    try {
                        server.disconnect()
                        updateConnectionState(server.id, false)
                        Log.i(TAG, "Disconnected from MCP server: ${server.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting from MCP server: ${server.id}", e)
                    }
                }
            }
        }
    }
    
    /**
     * Disconnect from a specific MCP server
     */
    suspend fun disconnectServer(serverId: String) {
        withContext(Dispatchers.IO) {
            val server = servers[serverId] ?: return@withContext
            
            try {
                server.disconnect()
                updateConnectionState(serverId, false)
                Log.i(TAG, "Disconnected from MCP server: $serverId")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from MCP server: $serverId", e)
            }
        }
    }
    
    /**
     * Update the connection state for a server
     */
    private fun updateConnectionState(serverId: String, connected: Boolean) {
        _connectionState.value = _connectionState.value.toMutableMap().apply {
            put(serverId, connected)
        }
        
        // Update registered servers list
        _registeredServers.value = _registeredServers.value.map { info ->
            if (info.id == serverId) {
                info.copy(isConnected = connected)
            } else {
                info
            }
        }
    }
    
    /**
     * List all tools from all connected MCP servers
     */
    suspend fun listAllTools(): List<McpToolInfo> {
        return withContext(Dispatchers.IO) {
            val allTools = mutableListOf<McpToolInfo>()
            
            servers.values.filter { it.isConnected() }.forEach { server ->
                try {
                    val tools = server.listTools()
                    allTools.addAll(tools.map { tool ->
                        McpToolInfo(
                            serverId = server.id,
                            serverName = server.name,
                            tool = tool
                        )
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Error listing tools from MCP server: ${server.id}", e)
                }
            }
            
            allTools
        }
    }
    
    /**
     * List all resources from all connected MCP servers
     */
    suspend fun listAllResources(): List<McpResourceInfo> {
        return withContext(Dispatchers.IO) {
            val allResources = mutableListOf<McpResourceInfo>()
            
            servers.values.filter { it.isConnected() }.forEach { server ->
                try {
                    val resources = server.listResources()
                    allResources.addAll(resources.map { resource ->
                        McpResourceInfo(
                            serverId = server.id,
                            serverName = server.name,
                            resource = resource
                        )
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Error listing resources from MCP server: ${server.id}", e)
                }
            }
            
            allResources
        }
    }
    
    /**
     * Execute a tool on an MCP server
     */
    suspend fun executeTool(
        serverId: String,
        toolName: String,
        parameters: Map<String, Any>
    ): McpToolResponse {
        return withContext(Dispatchers.IO) {
            val server = servers[serverId]
            
            if (server == null) {
                Log.e(TAG, "MCP server not found: $serverId")
                return@withContext McpToolResponse(
                    isError = true,
                    content = "MCP server not found: $serverId"
                )
            }
            
            if (!server.isConnected()) {
                Log.e(TAG, "MCP server not connected: $serverId")
                return@withContext McpToolResponse(
                    isError = true,
                    content = "MCP server not connected: $serverId"
                )
            }
            
            try {
                server.executeTool(toolName, parameters)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool $toolName on MCP server $serverId", e)
                McpToolResponse(
                    isError = true,
                    content = "Error executing tool: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Read a resource from an MCP server
     */
    suspend fun readResource(serverId: String, uri: String): McpResourceContent {
        return withContext(Dispatchers.IO) {
            val server = servers[serverId]
            
            if (server == null) {
                Log.e(TAG, "MCP server not found: $serverId")
                return@withContext McpResourceContent(
                    content = "MCP server not found: $serverId"
                )
            }
            
            if (!server.isConnected()) {
                Log.e(TAG, "MCP server not connected: $serverId")
                return@withContext McpResourceContent(
                    content = "MCP server not connected: $serverId"
                )
            }
            
            try {
                server.readResource(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading resource $uri from MCP server $serverId", e)
                McpResourceContent(
                    content = "Error reading resource: ${e.message}"
                )
            }
        }
    }
}

/**
 * Data class representing information about a registered MCP server
 */
data class McpServerInfo(
    val id: String,
    val name: String,
    val isConnected: Boolean
)

/**
 * Data class representing information about an MCP tool
 */
data class McpToolInfo(
    val serverId: String,
    val serverName: String,
    val tool: McpTool
)

/**
 * Data class representing information about an MCP resource
 */
data class McpResourceInfo(
    val serverId: String,
    val serverName: String,
    val resource: McpResource
)
