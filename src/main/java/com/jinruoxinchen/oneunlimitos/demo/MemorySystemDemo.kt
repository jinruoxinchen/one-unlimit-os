package com.jinruoxinchen.oneunlimitos.demo

import com.jinruoxinchen.oneunlimitos.llm.ClaudeApiClient
import com.jinruoxinchen.oneunlimitos.mcp.McpServerManager
import com.jinruoxinchen.oneunlimitos.mcp.implementations.MemoryMcpServer
import com.jinruoxinchen.oneunlimitos.memory.MemorySystem
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Proof of Concept demonstration for the Hybrid Memory System.
 * This class showcases the core functionality of the memory system by performing
 * operations through both direct API access and MCP tool calls.
 */
class MemorySystemDemo {
    
    // Dependencies
    private val llmClient = ClaudeApiClient()
    private val memorySystem = MemorySystem(llmClient)
    private val mcpServerManager = McpServerManager.getInstance()
    private val memoryMcpServer = MemoryMcpServer(memorySystem)
    
    // Demonstration agent IDs
    private val mainAgentId = "demo_agent"
    private val systemAgentId = "system_agent"
    
    /**
     * Initialize the demo
     */
    suspend fun initialize() {
        println("Initializing Memory System Demo...")
        
        // Initialize the memory system
        memorySystem.initialize()
        
        // Register the Memory MCP server
        mcpServerManager.registerServer(memoryMcpServer)
        mcpServerManager.connectServer(memoryMcpServer.id)
        
        println("Memory system initialized and MCP server registered.")
        println("------------------------------------------------------")
    }
    
    /**
     * Run the demo
     */
    suspend fun runDemo() {
        // Initialize first
        initialize()
        
        // Part 1: Demonstrate direct memory system API usage
        demonstrateDirectApi()
        
        // Part 2: Demonstrate MCP tool usage
        demonstrateMcpTools()
        
        // Part 3: Demonstrate specialized memory types
        demonstrateSpecializedMemory()
        
        // Part 4: Demonstrate knowledge graph and relationship queries
        demonstrateKnowledgeGraph()
        
        println("\nMemory System Demo completed successfully!")
    }
    
    /**
     * Demonstrate direct API usage
     */
    private suspend fun demonstrateDirectApi() {
        println("\n== Part 1: Direct Memory API Usage ==\n")
        
        // Store some memories
        println("Storing memories via direct API...")
        
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "User prefers dark mode in all applications",
            importance = 0.8f,
            tags = listOf("preference", "ui")
        )
        
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "User uses WeChat for most messaging",
            importance = 0.7f,
            tags = listOf("app_usage", "messaging")
        )
        
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "User has an important meeting at 3pm on Wednesdays",
            importance = 0.9f,
            tags = listOf("schedule", "important")
        )
        
        // Query memories
        println("\nQuerying memories about user preferences:")
        val preferencesResult = memorySystem.retrieveRelevantMemories(
            query = "What are the user's UI preferences?",
            tags = listOf("preference")
        )
        println(preferencesResult)
        
        println("\nQuerying memories about user schedule:")
        val scheduleResult = memorySystem.retrieveRelevantMemories(
            query = "What meetings does the user have?",
            tags = listOf("schedule")
        )
        println(scheduleResult)
        
        // Demonstrate importance-based filtering
        println("\nDemonstrating importance-based filtering:")
        val importantMemories = memorySystem.retrieveRelevantMemories(
            query = "What's important to remember about the user?",
            minImportance = 0.8f
        )
        println(importantMemories)
    }
    
    /**
     * Demonstrate MCP tool usage
     */
    private suspend fun demonstrateMcpTools() {
        println("\n== Part 2: MCP Tool Usage ==\n")
        
        // Store a memory via MCP
        println("Storing memory via MCP tool...")
        val storeResult = mcpServerManager.executeTool(
            serverId = memoryMcpServer.id,
            toolName = "store_memory",
            parameters = mapOf(
                "agent_id" to systemAgentId,
                "content" to "User frequently uses navigation apps in the morning",
                "importance" to 0.6,
                "tags" to listOf("habit", "morning_routine", "app_usage")
            )
        )
        println("Store result: $storeResult")
        
        // Retrieve memories via MCP
        println("\nRetrieving memories via MCP tool...")
        val retrieveResult = mcpServerManager.executeTool(
            serverId = memoryMcpServer.id,
            toolName = "retrieve_memories",
            parameters = mapOf(
                "query" to "What apps does the user use?",
                "tags" to listOf("app_usage")
            )
        )
        println("Retrieve result: $retrieveResult")
    }
    
    /**
     * Demonstrate specialized memory types
     */
    private suspend fun demonstrateSpecializedMemory() {
        println("\n== Part 3: Specialized Memory Types ==\n")
        
        // User preferences
        println("Storing user preferences...")
        memorySystem.storeUserPreference(
            key = "theme",
            value = "dark",
            category = "ui"
        )
        
        memorySystem.storeUserPreference(
            key = "font_size",
            value = "large",
            category = "ui"
        )
        
        memorySystem.storeUserPreference(
            key = "notification_sound",
            value = "chime",
            category = "notifications"
        )
        
        // Retrieve preferences
        println("\nRetrieving UI preferences:")
        val uiPrefs = memorySystem.getUserPreferencesContext("ui")
        println(uiPrefs)
        
        // Store interaction
        println("\nStoring user interaction...")
        memorySystem.storeInteraction(
            agentId = mainAgentId,
            userQuery = "What's the weather like today?",
            agentResponse = "It's currently sunny and 22°C in Shanghai"
        )
        
        // Retrieve interactions
        println("\nRetrieving recent interactions:")
        val interactions = memorySystem.getRecentInteractionsContext(mainAgentId)
        println(interactions)
        
        // Store device context
        println("\nStoring device context information...")
        memorySystem.storeDeviceContext(
            stateType = "battery",
            stateData = mapOf(
                "level" to 85,
                "charging" to true,
                "temperature" to 37.2
            )
        )
        
        // Retrieve device context
        println("\nRetrieving device context:")
        val deviceContext = memorySystem.getDeviceContextInfo("battery")
        println(deviceContext)
    }
    
    /**
     * Demonstrate knowledge graph and relationship queries
     */
    private suspend fun demonstrateKnowledgeGraph() {
        println("\n== Part 4: Knowledge Graph and Relationships ==\n")
        
        // Store related memories
        val meetingMemoryId = "mem_${Instant.now().toEpochMilli()}_1"
        val reminderMemoryId = "mem_${Instant.now().toEpochMilli()}_2"
        val locationMemoryId = "mem_${Instant.now().toEpochMilli()}_3"
        
        // Create meeting memory
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "User has a client meeting on Thursday at 2pm",
            importance = 0.9f,
            tags = listOf("meeting", "important"),
            relatedMemoryIds = listOf() // Will link others to this
        )
        
        // Create reminder with relationship to meeting
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "Remind user to prepare slide deck for client meeting",
            importance = 0.8f,
            tags = listOf("reminder", "work"),
            relatedMemoryIds = listOf(meetingMemoryId)
        )
        
        // Create location with relationship to meeting
        memorySystem.storeMemory(
            agentId = mainAgentId,
            content = "Client meeting will be held at Shanghai Tower, 32nd floor",
            importance = 0.7f,
            tags = listOf("location", "meeting"),
            relatedMemoryIds = listOf(meetingMemoryId)
        )
        
        // Query for related memories
        println("\nQuerying for memories related to the meeting:")
        val relatedMemories = memorySystem.retrieveRelatedMemories(meetingMemoryId)
        println(relatedMemories)
        
        // Query using semantic search + knowledge graph
        println("\nHybrid query combining semantic search and knowledge graph:")
        val meetingQuery = memorySystem.retrieveRelevantMemories(
            query = "What do I need to prepare for meetings?",
            tags = listOf("meeting", "reminder")
        )
        println(meetingQuery)
    }
    
    /**
     * Format output with timestamp
     */
    private fun log(message: String) {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        println("[${formatter.format(now)}] $message")
    }
    
    companion object {
        /**
         * Run the demo from a main method
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val demo = MemorySystemDemo()
            runBlocking {
                demo.runDemo()
            }
        }
    }
}
