package com.jinruoxinchen.oneunlimitos.tools

import android.util.Log
import com.jinruoxinchen.oneunlimitos.accessibility.OneAccessibilityService
import com.jinruoxinchen.oneunlimitos.mcp.McpServerManager
import com.jinruoxinchen.oneunlimitos.mcp.McpToolAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for all tools available to agents in the system.
 * Manages tool registration, discovery, and execution.
 */
class ToolRegistry : CoroutineScope {
    
    private val TAG = "ToolRegistry"
    
    override val coroutineContext = Dispatchers.Default
    
    // Map of tool ID to tool implementation
    private val tools = ConcurrentHashMap<String, Tool>()
    
    // Mapping of agent ID to list of permitted tool IDs
    private val agentToolPermissions = ConcurrentHashMap<String, MutableSet<String>>()
    
    // Reference to the accessibility service for system interaction
    private var accessibilityService: OneAccessibilityService? = null
    
    // Reference to the MCP server manager
    private lateinit var mcpServerManager: McpServerManager
    
    // MCP tool adapter factory
    private lateinit var mcpToolAdapterFactory: McpToolAdapterFactory
    
    /**
     * Initialize the tool registry
     */
    fun initialize(accessibilityService: OneAccessibilityService?) {
        Log.i(TAG, "Initializing tool registry")
        this.accessibilityService = accessibilityService
        
        // Initialize MCP server manager
        this.mcpServerManager = McpServerManager.getInstance()
        this.mcpServerManager.initialize()
        
        // Initialize MCP tool adapter factory
        this.mcpToolAdapterFactory = McpToolAdapterFactory(mcpServerManager)
        
        // Register default tools
        registerDefaultTools()
        
        // Listen for MCP server connections and register tools
        initializeMcpTools()
    }
    
    /**
     * Initialize MCP tools from connected servers
     */
    private fun initializeMcpTools() {
        launch {
            try {
                // Connect to all registered MCP servers
                mcpServerManager.connectAllServers()
                
                // Register MCP tools
                registerMcpTools()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MCP tools", e)
            }
        }
    }
    
    /**
     * Register tools from connected MCP servers
     */
    private suspend fun registerMcpTools() {
        try {
            val mcpTools = mcpToolAdapterFactory.createAllToolAdapters()
            
            mcpTools.forEach { tool ->
                registerTool(tool)
                
                // Grant the tool to all agents for now
                // In a real implementation, you would have proper permission control
                val allAgentIds = agentToolPermissions.keys()
                allAgentIds.forEach { agentId ->
                    grantToolToAgent(agentId, tool.id)
                }
            }
            
            Log.i(TAG, "Registered ${mcpTools.size} MCP tools")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering MCP tools", e)
        }
    }
    
    /**
     * Register core system tools
     */
    private fun registerDefaultTools() {
        // System navigation tools
        registerTool(AppLauncherTool(accessibilityService))
        registerTool(ClickTool(accessibilityService))
        registerTool(TextInputTool(accessibilityService))
        registerTool(ScrollTool(accessibilityService))
        registerTool(BackButtonTool(accessibilityService))
        registerTool(HomeTool(accessibilityService))
        
        // Information tools
        registerTool(UiAnalyzerTool(accessibilityService))
        registerTool(ScreenReaderTool(accessibilityService))
        registerTool(AppInfoTool(accessibilityService))
        
        // System control tools
        registerTool(NotificationTool(accessibilityService))
        registerTool(SettingsControlTool(accessibilityService))
        
        // Grant all tools to all agents for the POC
        // In a real implementation, you would have a proper permission system
        val allToolIds = tools.keys()
        agentToolPermissions["main_assistant"] = ConcurrentHashMap.newKeySet<String>().apply { 
            addAll(allToolIds.toList()) 
        }
        agentToolPermissions["ui_navigator"] = ConcurrentHashMap.newKeySet<String>().apply { 
            addAll(allToolIds.toList()) 
        }
        agentToolPermissions["data_processor"] = ConcurrentHashMap.newKeySet<String>().apply { 
            addAll(allToolIds.toList()) 
        }
    }
    
    /**
     * Register a new tool
     */
    fun registerTool(tool: Tool) {
        tools[tool.id] = tool
        Log.d(TAG, "Registered tool: ${tool.id}")
    }
    
    /**
     * Grant a tool to an agent
     */
    fun grantToolToAgent(agentId: String, toolId: String) {
        val agentTools = agentToolPermissions.getOrPut(agentId) { 
            ConcurrentHashMap.newKeySet() 
        }
        agentTools.add(toolId)
    }
    
    /**
     * Revoke a tool from an agent
     */
    fun revokeToolFromAgent(agentId: String, toolId: String) {
        agentToolPermissions[agentId]?.remove(toolId)
    }
    
    /**
     * Get a tool by ID
     */
    fun getTool(toolId: String): Tool? {
        return tools[toolId]
    }
    
    /**
     * Get all tools available to an agent
     */
    fun getToolsForAgent(agentId: String): List<Tool> {
        val permittedToolIds = agentToolPermissions[agentId] ?: emptySet()
        return permittedToolIds.mapNotNull { tools[it] }
    }
    
    /**
     * Check if an agent has permission to use a tool
     */
    fun canAgentUseTool(agentId: String, toolId: String): Boolean {
        val permittedToolIds = agentToolPermissions[agentId] ?: emptySet()
        return permittedToolIds.contains(toolId)
    }
}

/**
 * Interface for all tools that can be used by agents
 */
interface Tool {
    val id: String
    val name: String
    val description: String
    val parameters: Map<String, ParameterDefinition>
    
    /**
     * Execute the tool with the given parameters
     */
    suspend fun execute(parameters: Map<String, Any>): String
    
    /**
     * Get the tool's schema as a JSON string
     */
    fun getSchema(): ToolSchema {
        return ToolSchema(
            id = id,
            name = name,
            description = description,
            parameters = parameters.mapValues { it.value.toSchemaParameter() }
        )
    }
}

/**
 * Parameter definition for tool inputs
 */
data class ParameterDefinition(
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
) {
    fun toSchemaParameter(): SchemaParameter {
        return SchemaParameter(
            type = type.name.lowercase(),
            description = description,
            required = required
        )
    }
}

/**
 * Supported parameter types
 */
enum class ParameterType {
    STRING,
    INTEGER,
    BOOLEAN,
    FLOAT,
    ARRAY,
    OBJECT
}

/**
 * Schema representation of a tool for LLM consumption
 */
@Serializable
data class ToolSchema(
    val id: String,
    val name: String,
    val description: String,
    val parameters: Map<String, SchemaParameter>
)

/**
 * Schema representation of a parameter for LLM consumption
 */
@Serializable
data class SchemaParameter(
    val type: String,
    val description: String,
    val required: Boolean = true
)

/**
 * Tool implementations
 */

/**
 * Tool to launch an app by package name
 */
class AppLauncherTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "app_launcher"
    override val name: String = "App Launcher"
    override val description: String = "Launches an app by its package name"
    override val parameters = mapOf(
        "packageName" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The package name of the app to launch"
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val packageName = parameters["packageName"] as? String
            ?: return "Error: Package name is required"
        
        return withContext(Dispatchers.IO) {
            try {
                // In a real implementation, this would use the accessibility service
                // to launch the app using intents
                "Launched app: $packageName"
            } catch (e: Exception) {
                Log.e("AppLauncherTool", "Error launching app", e)
                "Error launching app: ${e.message}"
            }
        }
    }
}

/**
 * Tool to click on a UI element
 */
class ClickTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "click"
    override val name: String = "Click"
    override val description: String = "Clicks on a UI element identified by text or description"
    override val parameters = mapOf(
        "elementText" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The text or content description of the element to click"
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val elementText = parameters["elementText"] as? String
            ?: return "Error: Element text is required"
        
        return withContext(Dispatchers.IO) {
            try {
                val node = accessibilityService?.findElementByText(elementText)
                
                if (node == null) {
                    "Element with text '$elementText' not found"
                } else {
                    val success = accessibilityService?.performClick(node) ?: false
                    if (success) {
                        "Clicked on element with text: $elementText"
                    } else {
                        "Failed to click on element with text: $elementText"
                    }
                }
            } catch (e: Exception) {
                Log.e("ClickTool", "Error clicking element", e)
                "Error clicking element: ${e.message}"
            }
        }
    }
}

/**
 * Tool to input text
 */
class TextInputTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "text_input"
    override val name: String = "Text Input"
    override val description: String = "Types text into the currently focused input field"
    override val parameters = mapOf(
        "text" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The text to type"
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val text = parameters["text"] as? String
            ?: return "Error: Text is required"
        
        return withContext(Dispatchers.IO) {
            try {
                val success = accessibilityService?.typeText(text) ?: false
                if (success) {
                    "Typed text: $text"
                } else {
                    "Failed to type text. Make sure an input field is focused."
                }
            } catch (e: Exception) {
                Log.e("TextInputTool", "Error typing text", e)
                "Error typing text: ${e.message}"
            }
        }
    }
}

/**
 * Tool to scroll in a direction
 */
class ScrollTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "scroll"
    override val name: String = "Scroll"
    override val description: String = "Scrolls in the specified direction (up, down, left, right)"
    override val parameters = mapOf(
        "direction" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The direction to scroll (up, down, left, right)"
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val directionStr = (parameters["direction"] as? String)?.lowercase()
            ?: return "Error: Direction is required"
        
        // This would be replaced with actual enum in a real implementation
        val direction = when (directionStr) {
            "up" -> com.jinruoxinchen.oneunlimitos.accessibility.InteractionController.ScrollDirection.UP
            "down" -> com.jinruoxinchen.oneunlimitos.accessibility.InteractionController.ScrollDirection.DOWN
            "left" -> com.jinruoxinchen.oneunlimitos.accessibility.InteractionController.ScrollDirection.LEFT
            "right" -> com.jinruoxinchen.oneunlimitos.accessibility.InteractionController.ScrollDirection.RIGHT
            else -> return "Error: Invalid direction. Use up, down, left, or right."
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val success = accessibilityService?.scroll(direction) ?: false
                if (success) {
                    "Scrolled $directionStr"
                } else {
                    "Failed to scroll $directionStr"
                }
            } catch (e: Exception) {
                Log.e("ScrollTool", "Error scrolling", e)
                "Error scrolling: ${e.message}"
            }
        }
    }
}

/**
 * Tool to press the back button
 */
class BackButtonTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "back_button"
    override val name: String = "Back Button"
    override val description: String = "Presses the system back button"
    override val parameters = mapOf<String, ParameterDefinition>()
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                // In a real implementation, this would use the accessibility service
                // to perform a global back action
                "Pressed back button"
            } catch (e: Exception) {
                Log.e("BackButtonTool", "Error pressing back button", e)
                "Error pressing back button: ${e.message}"
            }
        }
    }
}

/**
 * Tool to press the home button
 */
class HomeTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "home_button"
    override val name: String = "Home Button"
    override val description: String = "Presses the system home button"
    override val parameters = mapOf<String, ParameterDefinition>()
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                // In a real implementation, this would use the accessibility service
                // to perform a global home action
                "Pressed home button"
            } catch (e: Exception) {
                Log.e("HomeTool", "Error pressing home button", e)
                "Error pressing home button: ${e.message}"
            }
        }
    }
}

/**
 * Tool to analyze the current UI
 */
class UiAnalyzerTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "ui_analyzer"
    override val name: String = "UI Analyzer"
    override val description: String = "Analyzes the current UI and provides a structured representation"
    override val parameters = mapOf<String, ParameterDefinition>()
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                val uiState = accessibilityService?.getCurrentUiState()
                uiState?.toString() ?: "Unable to analyze UI state"
            } catch (e: Exception) {
                Log.e("UiAnalyzerTool", "Error analyzing UI", e)
                "Error analyzing UI: ${e.message}"
            }
        }
    }
}

/**
 * Tool to read screen content
 */
class ScreenReaderTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "screen_reader"
    override val name: String = "Screen Reader"
    override val description: String = "Reads and summarizes the content currently displayed on screen"
    override val parameters = mapOf<String, ParameterDefinition>()
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                // In a real implementation, this would analyze the current UI hierarchy
                // and extract readable content
                "Screen content summary would appear here"
            } catch (e: Exception) {
                Log.e("ScreenReaderTool", "Error reading screen", e)
                "Error reading screen: ${e.message}"
            }
        }
    }
}

/**
 * Tool to get information about the current app
 */
class AppInfoTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "app_info"
    override val name: String = "App Information"
    override val description: String = "Gets information about the currently active app"
    override val parameters = mapOf<String, ParameterDefinition>()
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            try {
                // In a real implementation, this would get package info for the current app
                "App information would appear here"
            } catch (e: Exception) {
                Log.e("AppInfoTool", "Error getting app info", e)
                "Error getting app info: ${e.message}"
            }
        }
    }
}

/**
 * Tool to interact with notifications
 */
class NotificationTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "notification"
    override val name: String = "Notification Tool"
    override val description: String = "Interacts with system notifications"
    override val parameters = mapOf(
        "action" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The action to perform (list, open, dismiss)"
        ),
        "id" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The notification ID for open/dismiss actions",
            required = false
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val action = (parameters["action"] as? String)?.lowercase()
            ?: return "Error: Action is required"
        
        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "list" -> "Notification list would appear here"
                    "open" -> {
                        val id = parameters["id"] as? String
                            ?: return@withContext "Error: Notification ID is required for open action"
                        "Opened notification with ID: $id"
                    }
                    "dismiss" -> {
                        val id = parameters["id"] as? String
                            ?: return@withContext "Error: Notification ID is required for dismiss action"
                        "Dismissed notification with ID: $id"
                    }
                    else -> "Error: Invalid action. Use list, open, or dismiss."
                }
            } catch (e: Exception) {
                Log.e("NotificationTool", "Error with notification action", e)
                "Error with notification action: ${e.message}"
            }
        }
    }
}

/**
 * Tool to control system settings
 */
class SettingsControlTool(private val accessibilityService: OneAccessibilityService?) : Tool {
    override val id: String = "settings_control"
    override val name: String = "Settings Control"
    override val description: String = "Controls system settings like Wi-Fi, Bluetooth, brightness"
    override val parameters = mapOf(
        "setting" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The setting to control (wifi, bluetooth, brightness, volume)"
        ),
        "action" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The action to perform (get, enable, disable, set)"
        ),
        "value" to ParameterDefinition(
            type = ParameterType.STRING,
            description = "The value to set (for 'set' action)",
            required = false
        )
    )
    
    override suspend fun execute(parameters: Map<String, Any>): String {
        val setting = (parameters["setting"] as? String)?.lowercase()
            ?: return "Error: Setting is required"
        val action = (parameters["action"] as? String)?.lowercase()
            ?: return "Error: Action is required"
        
        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "get" -> "Current $setting status would appear here"
                    "enable" -> "Enabled $setting"
                    "disable" -> "Disabled $setting"
                    "set" -> {
                        val value = parameters["value"] as? String
                            ?: return@withContext "Error: Value is required for set action"
                        "Set $setting to $value"
                    }
                    else -> "Error: Invalid action. Use get, enable, disable, or set."
                }
            } catch (e: Exception) {
                Log.e("SettingsControlTool", "Error controlling setting", e)
                "Error controlling setting: ${e.message}"
            }
        }
    }
}
