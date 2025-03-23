package com.jinruoxinchen.oneunlimitos.agents

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinruoxinchen.oneunlimitos.llm.ClaudeApiClient
import com.jinruoxinchen.oneunlimitos.memory.MemorySystem
import com.jinruoxinchen.oneunlimitos.tools.Tool
import com.jinruoxinchen.oneunlimitos.tools.ToolRegistry

/**
 * Base abstract class for all agents in the system.
 * Agents are AI entities that can process requests, use tools, and interact with the system.
 */
abstract class Agent(
    val id: String,
    val name: String,
    protected val llmClient: ClaudeApiClient,
    protected val memorySystem: MemorySystem,
    protected val toolRegistry: ToolRegistry
) {
    /**
     * Process a request and generate a response
     */
    abstract suspend fun processRequest(request: String): AgentResponse
    
    /**
     * Handle system events (e.g., accessibility events)
     */
    abstract suspend fun onSystemEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?)
    
    /**
     * Use a tool by its ID
     */
    protected suspend fun useTool(toolId: String, parameters: Map<String, Any>): String {
        val tool = toolRegistry.getTool(toolId) ?: throw IllegalArgumentException("Tool not found: $toolId")
        return tool.execute(parameters)
    }
    
    /**
     * Get available tools for this agent
     */
    protected fun getAvailableTools(): List<Tool> {
        return toolRegistry.getToolsForAgent(id)
    }
    
    /**
     * Generate a system prompt for the agent
     */
    protected fun generateSystemPrompt(): String {
        val basePrompt = """
            You are ${name}, an AI agent in the One Unlimited OS system. 
            You help users interact with their Android device by understanding their requests and executing actions.
            You have access to the following tools: ${getAvailableTools().joinToString(", ") { it.name }}.
            
            When responding to user requests:
            1. Analyze what the user is asking for
            2. Identify the necessary actions to fulfill the request
            3. Determine which tools you need to use
            4. Provide a clear, helpful response
            
            Your responses should be natural, helpful, and focused on completing the requested task.
        """.trimIndent()
        
        // Add agent-specific instructions
        val agentSpecificPrompt = getAgentSpecificPrompt()
        
        return "$basePrompt\n\n$agentSpecificPrompt"
    }
    
    /**
     * Get agent-specific prompt additions
     */
    protected abstract fun getAgentSpecificPrompt(): String
    
    /**
     * Get relevant memories for a given request
     */
    protected suspend fun getRelevantMemories(request: String): String {
        return memorySystem.retrieveRelevantMemories(request)
    }
    
    /**
     * Save a new memory
     */
    protected suspend fun saveMemory(content: String, importance: Float = 1.0f) {
        memorySystem.storeMemory(id, content, importance)
    }
}

/**
 * Main assistant agent that handles general user requests
 */
class MainAssistantAgent(
    id: String,
    name: String,
    llmClient: ClaudeApiClient,
    memorySystem: MemorySystem,
    toolRegistry: ToolRegistry
) : Agent(id, name, llmClient, memorySystem, toolRegistry) {
    
    override suspend fun processRequest(request: String): AgentResponse {
        // Retrieve relevant memories
        val relevantMemories = getRelevantMemories(request)
        
        // Prepare context with current system state and memories
        val context = """
            Current memories related to this request:
            $relevantMemories
            
            Available tools:
            ${getAvailableTools().joinToString("\n") { "- ${it.name}: ${it.description}" }}
        """.trimIndent()
        
        // Get response from LLM
        val response = llmClient.sendMessage(
            systemPrompt = generateSystemPrompt(),
            userMessage = request,
            context = context
        )
        
        // Parse response for any actions (in a real implementation, you'd have structured responses)
        // This is a simplified version for the POC
        val actions = parseActionsFromResponse(response)
        
        // Store this interaction in memory
        saveMemory("User asked: $request\nResponse: $response")
        
        return AgentResponse(response, actions)
    }
    
    override suspend fun onSystemEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        // For the main assistant, we might only care about major events like app changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            saveMemory("User navigated to app: $packageName")
        }
    }
    
    override fun getAgentSpecificPrompt(): String {
        return """
            As the main assistant, your role is to understand user intent and coordinate actions.
            You should maintain a conversational tone and provide clear feedback about actions taken.
            If you need specialized capabilities, you can delegate to other agents in the system.
        """.trimIndent()
    }
    
    private fun parseActionsFromResponse(response: String): List<SystemAction> {
        // In a real implementation, you would parse structured outputs from the LLM
        // For this POC, we're keeping it simple
        return emptyList()
    }
}

/**
 * Specialized agent for UI navigation
 */
class UiNavigationAgent(
    id: String,
    name: String,
    llmClient: ClaudeApiClient,
    memorySystem: MemorySystem,
    toolRegistry: ToolRegistry
) : Agent(id, name, llmClient, memorySystem, toolRegistry) {
    
    override suspend fun processRequest(request: String): AgentResponse {
        // Implementation for UI navigation requests
        val response = llmClient.sendMessage(
            systemPrompt = generateSystemPrompt(),
            userMessage = request
        )
        
        // TODO: Parse response for navigation actions
        
        return AgentResponse(response)
    }
    
    override suspend fun onSystemEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        // Track UI changes more closely for navigation agent
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Update UI state in memory
                rootNode?.let { node ->
                    // Store information about the current UI state
                    // In a real implementation, you would process the node tree
                }
            }
        }
    }
    
    override fun getAgentSpecificPrompt(): String {
        return """
            As the navigation agent, your primary role is to help users navigate the device UI.
            You should understand UI hierarchies, find elements, and perform actions like clicking, 
            scrolling, and typing. Focus on efficient and accurate navigation.
        """.trimIndent()
    }
}

/**
 * Specialized agent for data processing
 */
class DataProcessingAgent(
    id: String,
    name: String,
    llmClient: ClaudeApiClient,
    memorySystem: MemorySystem,
    toolRegistry: ToolRegistry
) : Agent(id, name, llmClient, memorySystem, toolRegistry) {
    
    override suspend fun processRequest(request: String): AgentResponse {
        // Implementation for data processing requests
        val response = llmClient.sendMessage(
            systemPrompt = generateSystemPrompt(),
            userMessage = request
        )
        
        return AgentResponse(response)
    }
    
    override suspend fun onSystemEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        // Data processing agent might be interested in content changes that contain data
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Extract and process data if relevant
        }
    }
    
    override fun getAgentSpecificPrompt(): String {
        return """
            As the data processing agent, your role is to extract, analyze, and transform data.
            You should be able to work with text, numbers, and structured information from apps.
            Focus on accuracy and providing useful insights from the data.
        """.trimIndent()
    }
}
