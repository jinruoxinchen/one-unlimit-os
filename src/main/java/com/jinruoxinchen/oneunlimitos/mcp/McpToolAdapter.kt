package com.jinruoxinchen.oneunlimitos.mcp

import android.util.Log
import com.jinruoxinchen.oneunlimitos.tools.ParameterDefinition
import com.jinruoxinchen.oneunlimitos.tools.ParameterType
import com.jinruoxinchen.oneunlimitos.tools.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter that converts an MCP tool into the standard Tool interface.
 * This allows MCP tools to be seamlessly integrated into the tool registry.
 */
class McpToolAdapter(
    private val mcpToolInfo: McpToolInfo,
    private val mcpServerManager: McpServerManager
) : Tool {
    
    private val TAG = "McpToolAdapter"
    
    override val id: String = "mcp_${mcpToolInfo.serverId}_${mcpToolInfo.tool.name}"
    override val name: String = "${mcpToolInfo.serverName} - ${mcpToolInfo.tool.name}"
    override val description: String = mcpToolInfo.tool.description
    
    // Convert MCP tool parameters to Tool parameters
    override val parameters: Map<String, ParameterDefinition> = mcpToolInfo.tool.inputSchema.mapValues { (_, schema) ->
        ParameterDefinition(
            type = convertParameterType(schema.type),
            description = schema.description,
            required = schema.required
        )
    }
    
    /**
     * Execute the MCP tool with the given parameters
     */
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = mcpServerManager.executeTool(
                    serverId = mcpToolInfo.serverId,
                    toolName = mcpToolInfo.tool.name,
                    parameters = parameters
                )
                
                if (response.isError) {
                    Log.e(TAG, "Error executing MCP tool: ${mcpToolInfo.tool.name}")
                    "Error: ${response.content}"
                } else {
                    response.content
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception executing MCP tool: ${mcpToolInfo.tool.name}", e)
                "Error: ${e.message}"
            }
        }
    }
    
    /**
     * Convert MCP parameter type to Tool parameter type
     */
    private fun convertParameterType(type: String): ParameterType {
        return when (type.lowercase()) {
            "string" -> ParameterType.STRING
            "integer", "number" -> ParameterType.INTEGER
            "boolean" -> ParameterType.BOOLEAN
            "float", "double" -> ParameterType.FLOAT
            "array" -> ParameterType.ARRAY
            "object" -> ParameterType.OBJECT
            else -> ParameterType.STRING // Default to string for unknown types
        }
    }
}

/**
 * Factory for creating MCP tool adapters
 */
class McpToolAdapterFactory(
    private val mcpServerManager: McpServerManager
) {
    /**
     * Create tool adapters for all tools from all connected MCP servers
     */
    suspend fun createAllToolAdapters(): List<Tool> {
        val mcpTools = mcpServerManager.listAllTools()
        return mcpTools.map { mcpToolInfo ->
            McpToolAdapter(mcpToolInfo, mcpServerManager)
        }
    }
    
    /**
     * Create tool adapters for all tools from a specific MCP server
     */
    suspend fun createToolAdaptersForServer(serverId: String): List<Tool> {
        val mcpTools = mcpServerManager.listAllTools().filter { it.serverId == serverId }
        return mcpTools.map { mcpToolInfo ->
            McpToolAdapter(mcpToolInfo, mcpServerManager)
        }
    }
}
