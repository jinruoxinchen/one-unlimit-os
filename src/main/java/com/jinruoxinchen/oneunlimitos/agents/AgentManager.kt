package com.jinruoxinchen.oneunlimitos.agents

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinruoxinchen.oneunlimitos.accessibility.OneAccessibilityService
import com.jinruoxinchen.oneunlimitos.llm.ClaudeApiClient
import com.jinruoxinchen.oneunlimitos.mcp.McpServerManager
import com.jinruoxinchen.oneunlimitos.mcp.implementations.MemoryMcpServer
import com.jinruoxinchen.oneunlimitos.mcp.implementations.PersonalInfoMcpServer
import com.jinruoxinchen.oneunlimitos.mcp.implementations.WeChatMcpServer
import com.jinruoxinchen.oneunlimitos.memory.MemorySystem
import com.jinruoxinchen.oneunlimitos.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Central manager for AI agents that coordinates their activities, manages their lifecycle,
 * and brokers communication between agents and the system.
 */
class AgentManager : CoroutineScope {
    
    private val TAG = "AgentManager"
    
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    
    // Singleton instance
    companion object {
        @Volatile
        private var instance: AgentManager? = null
        
        fun getInstance(): AgentManager {
            return instance ?: synchronized(this) {
                instance ?: AgentManager().also { instance = it }
            }
        }
    }
    
    // The LLM client for agent intelligence
    private val llmClient = ClaudeApiClient()
    
    // Registry of available agents
    private val agents = mutableMapOf<String, Agent>()
    
    // Memory system for context and agent memory
    private val memorySystem = MemorySystem()
    
    // Tool registry for agent capabilities
    private val toolRegistry = ToolRegistry()
    
    // Reference to the accessibility service
    private var accessibilityService: OneAccessibilityService? = null
    
    // MCP server manager
    private val mcpServerManager = McpServerManager.getInstance()
    
    /**
     * Called when the accessibility service is connected and ready
     */
    fun onAccessibilityServiceConnected(service: OneAccessibilityService) {
        accessibilityService = service
        
        // Initialize the system with the connected service
        launch {
            initializeSystem()
        }
    }
    
    /**
     * Initialize the agent system components
     */
    private suspend fun initializeSystem() {
        // Initialize memory system
        memorySystem.initialize()
        
        // Initialize tool registry
        toolRegistry.initialize(accessibilityService)
        
        // Initialize and register MCP servers
        initializeMcpServers()
        
        // Create and register default agents
        createDefaultAgents()
    }
    
    /**
     * Initialize and register MCP servers
     */
    private fun initializeMcpServers() {
        // Initialize WeChat MCP server
        val weChatMcpServer = WeChatMcpServer(accessibilityService)
        mcpServerManager.registerServer(weChatMcpServer)
        
        // Initialize Personal Information MCP server
        val personalInfoMcpServer = PersonalInfoMcpServer(accessibilityService)
        mcpServerManager.registerServer(personalInfoMcpServer)
        
        // Initialize Memory MCP server
        val memoryMcpServer = MemoryMcpServer(memorySystem)
        mcpServerManager.registerServer(memoryMcpServer)
        
        // Note: Additional MCP servers would be registered here as needed
        
        Log.i(TAG, "Registered MCP servers")
    }
    
    /**
     * Create and register the default set of agents
     */
    private fun createDefaultAgents() {
        // Create main assistant agent
        val mainAgent = MainAssistantAgent(
            id = "main_assistant",
            name = "Assistant",
            llmClient = llmClient,
            memorySystem = memorySystem,
            toolRegistry = toolRegistry
        )
        
        // Create specialized agents as needed
        val uiNavigationAgent = UiNavigationAgent(
            id = "ui_navigator",
            name = "Navigator",
            llmClient = llmClient,
            memorySystem = memorySystem,
            toolRegistry = toolRegistry
        )
        
        val dataProcessingAgent = DataProcessingAgent(
            id = "data_processor",
            name = "Data Processor",
            llmClient = llmClient,
            memorySystem = memorySystem,
            toolRegistry = toolRegistry
        )
        
        // Register agents
        registerAgent(mainAgent)
        registerAgent(uiNavigationAgent)
        registerAgent(dataProcessingAgent)
    }
    
    /**
     * Register an agent with the manager
     */
    fun registerAgent(agent: Agent) {
        agents[agent.id] = agent
    }
    
    /**
     * Get an agent by ID
     */
    fun getAgent(id: String): Agent? {
        return agents[id]
    }
    
    /**
     * Process a user request through the appropriate agent(s)
     */
    suspend fun processUserRequest(request: String): AgentResponse {
        // For simple demo, use the main assistant agent
        val mainAgent = agents["main_assistant"] ?: throw IllegalStateException("Main agent not found")
        
        // Process the request through the agent
        return mainAgent.processRequest(request)
    }
    
    /**
     * Handle accessibility events from the system
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        launch {
            // Update memory system with new observations
            memorySystem.addObservation(event, rootNode)
            
            // Notify relevant agents of the event
            agents.values.forEach { agent ->
                agent.onSystemEvent(event, rootNode)
            }
        }
    }
    
    /**
     * Execute a system action through the accessibility service
     */
    fun executeSystemAction(action: SystemAction): Boolean {
        val service = accessibilityService ?: return false
        
        return when (action) {
            is SystemAction.Click -> {
                action.node?.let { service.performClick(it) } ?: false
            }
            is SystemAction.TypeText -> {
                service.typeText(action.text)
            }
            is SystemAction.Scroll -> {
                service.scroll(action.direction)
            }
            // Add other action types as needed
        }
    }
}

/**
 * Sealed class representing different system actions that an agent can request
 */
sealed class SystemAction {
    data class Click(val node: AccessibilityNodeInfo?) : SystemAction()
    data class TypeText(val text: String) : SystemAction()
    data class Scroll(val direction: com.jinruoxinchen.oneunlimitos.accessibility.InteractionController.ScrollDirection) : SystemAction()
    // Add more action types as needed
}

/**
 * Response from an agent to a user request
 */
data class AgentResponse(
    val text: String,
    val actions: List<SystemAction> = emptyList()
)
