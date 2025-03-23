package com.jinruoxinchen.oneunlimitos.demo

import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Proof of Concept Runner for the Hybrid Memory System.
 * This is a simplified mock implementation for demonstration purposes
 * that removes Android dependencies to allow running as a standalone application.
 */
class MemorySystemPoCRunner {
    
    // Mock implementation of system components for demo purposes
    private val mockMemorySystem = MockMemorySystem()
    
    /**
     * Run the demo
     */
    fun runDemo() {
        println("========================================================")
        println("    One Unlimited OS - Hybrid Memory System PoC Demo    ")
        println("========================================================")
        println("This is a simplified mock implementation for demonstration")
        println("purposes that simulates the hybrid memory system.")
        println()
        
        // Part 1: Vector Database Demonstration
        demonstrateVectorDatabase()
        
        // Part 2: Knowledge Graph Demonstration
        demonstrateKnowledgeGraph()
        
        // Part 3: Specialized Memory Demonstration
        demonstrateSpecializedMemory()
        
        // Part 4: Hybrid Memory Integration
        demonstrateHybridMemory()
        
        println("\nMemory System PoC Demo completed successfully!")
    }
    
    /**
     * Demonstrate vector database functionality
     */
    private fun demonstrateVectorDatabase() {
        log("\n== PART 1: VECTOR DATABASE MEMORY ==")
        
        log("Storing memories in vector database...")
        mockMemorySystem.storeMemory("User prefers dark mode in all applications")
        mockMemorySystem.storeMemory("User frequently listens to classical music")
        mockMemorySystem.storeMemory("User usually commutes to work by subway")
        mockMemorySystem.storeMemory("User has scheduled a dentist appointment for next Tuesday")
        
        log("\nPerforming semantic search:")
        
        val query1 = "What are the user's preferences?"
        log("Query: \"$query1\"")
        log("Result: " + mockMemorySystem.searchMemories(query1))
        
        val query2 = "How does the user get to work?"
        log("Query: \"$query2\"")
        log("Result: " + mockMemorySystem.searchMemories(query2))
        
        val query3 = "What appointments does the user have?"
        log("Query: \"$query3\"")
        log("Result: " + mockMemorySystem.searchMemories(query3))
    }
    
    /**
     * Demonstrate knowledge graph functionality
     */
    private fun demonstrateKnowledgeGraph() {
        log("\n== PART 2: KNOWLEDGE GRAPH MEMORY ==")
        
        log("Creating entities and relationships...")
        
        // Create entities
        mockMemorySystem.createEntity("Meeting", "Work", "Weekly team sync meeting on Mondays at 10am")
        mockMemorySystem.createEntity("John", "Person", "Team lead, prefers detailed reports")
        mockMemorySystem.createEntity("Presentation", "Document", "Q1 results presentation")
        mockMemorySystem.createEntity("Conference Room A", "Location", "4th floor, has video conferencing")
        
        // Create relationships
        mockMemorySystem.createRelation("Meeting", "John", "attended by")
        mockMemorySystem.createRelation("Meeting", "Presentation", "includes")
        mockMemorySystem.createRelation("Meeting", "Conference Room A", "located at")
        
        log("\nQuerying related entities:")
        log("Entities related to 'Meeting':")
        mockMemorySystem.getRelatedEntities("Meeting").forEach { log("- $it") }
        
        log("\nTraversing relationships:")
        log("Where is the meeting that includes the presentation?")
        log("Result: " + mockMemorySystem.resolveQuery("Meeting location with presentation"))
    }
    
    /**
     * Demonstrate specialized memory types
     */
    private fun demonstrateSpecializedMemory() {
        log("\n== PART 3: SPECIALIZED MEMORY TYPES ==")
        
        log("Storing user preferences...")
        mockMemorySystem.storeUserPreference("theme", "dark")
        mockMemorySystem.storeUserPreference("font_size", "large")
        mockMemorySystem.storeUserPreference("notifications", "enabled")
        
        log("\nStoring app state...")
        mockMemorySystem.storeAppState("com.example.calendar", "ViewMonth", mapOf(
            "current_month" to "April",
            "current_year" to "2025",
            "selected_date" to "2025-04-15"
        ))
        
        log("\nStoring interaction history...")
        mockMemorySystem.storeInteraction(
            "User: What's on my schedule today?",
            "Assistant: You have a team meeting at 10am and lunch with John at 12:30pm."
        )
        mockMemorySystem.storeInteraction(
            "User: Remind me to buy milk",
            "Assistant: I've added 'buy milk' to your shopping list."
        )
        
        log("\nRetrieving user preferences:")
        log(mockMemorySystem.getUserPreferences())
        
        log("\nRetrieving app state:")
        log(mockMemorySystem.getAppState("com.example.calendar"))
        
        log("\nRetrieving recent interactions:")
        log(mockMemorySystem.getRecentInteractions())
    }
    
    /**
     * Demonstrate hybrid memory integration
     */
    private fun demonstrateHybridMemory() {
        log("\n== PART 4: HYBRID MEMORY INTEGRATION ==")
        
        log("Demonstrating a complex query across memory systems...")
        
        val query = "What do I need to prepare for the meeting with John?"
        log("Query: \"$query\"")
        
        // This would combine results from vector DB, knowledge graph, and specialized memory
        log("Result:")
        log("1. [Vector Memory] Found meeting entry: 'Weekly team sync meeting on Mondays at 10am'")
        log("2. [Knowledge Graph] Related entity 'Presentation': 'Q1 results presentation'")
        log("3. [Knowledge Graph] Related entity 'John': 'Team lead, prefers detailed reports'")
        log("4. [User Preferences] John prefers detailed reports")
        log("5. [App State] Meeting located in 'Conference Room A' with video conferencing")
        
        log("\nSynthesized answer:")
        log("You need to prepare the Q1 results presentation for the Monday 10am meeting with John.")
        log("John prefers detailed reports, so ensure your presentation is comprehensive.")
        log("The meeting will be in Conference Room A on the 4th floor, which has video conferencing equipment.")
    }
    
    /**
     * Format output with timestamp
     */
    private fun log(message: String) {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        println("[${formatter.format(now)}] $message")
    }
    
    /**
     * Mock Memory System implementation for demonstration purposes
     */
    inner class MockMemorySystem {
        private val memories = mutableListOf<String>()
        private val entities = mutableMapOf<String, Pair<String, String>>() // name -> (type, description)
        private val relations = mutableListOf<Triple<String, String, String>>() // from, to, type
        private val preferences = mutableMapOf<String, String>()
        private val appStates = mutableMapOf<String, Pair<String, Map<String, String>>>() // package -> (state, data)
        private val interactions = mutableListOf<Pair<String, String>>() // user, assistant
        
        fun storeMemory(content: String) {
            memories.add(content)
        }
        
        fun searchMemories(query: String): String {
            // Simple mock implementation of semantic search
            val words = query.lowercase().split(" ")
            val bestMatch = memories.maxByOrNull { memory ->
                words.count { word -> memory.lowercase().contains(word) }
            } ?: return "No relevant memories found."
            
            return bestMatch
        }
        
        fun createEntity(name: String, type: String, description: String) {
            entities[name] = Pair(type, description)
        }
        
        fun createRelation(from: String, to: String, type: String) {
            relations.add(Triple(from, to, type))
        }
        
        fun getRelatedEntities(entityName: String): List<String> {
            val result = mutableListOf<String>()
            
            // Find relations where this entity is the source
            relations.filter { it.first == entityName }.forEach { relation ->
                val targetEntity = entities[relation.second]
                if (targetEntity != null) {
                    result.add("${relation.second} (${targetEntity.first}): ${targetEntity.second} - Relation: ${relation.third}")
                }
            }
            
            // Find relations where this entity is the target
            relations.filter { it.second == entityName }.forEach { relation ->
                val sourceEntity = entities[relation.first]
                if (sourceEntity != null) {
                    result.add("${relation.first} (${sourceEntity.first}): ${sourceEntity.second} - Relation: ${relation.third} (inbound)")
                }
            }
            
            return result
        }
        
        fun resolveQuery(query: String): String {
            // Mock implementation that simulates resolving a complex query using the knowledge graph
            if (query.contains("location") && query.contains("presentation")) {
                // Find where the meeting with a presentation is located
                val meetingRelations = relations.filter { it.first == "Meeting" && it.third == "includes" && it.second == "Presentation" }
                if (meetingRelations.isNotEmpty()) {
                    val locationRelation = relations.find { it.first == "Meeting" && it.third == "located at" }
                    if (locationRelation != null) {
                        val location = entities[locationRelation.second]
                        if (location != null) {
                            return "${locationRelation.second}: ${location.second}"
                        }
                    }
                }
            }
            
            return "Could not resolve query: $query"
        }
        
        fun storeUserPreference(key: String, value: String) {
            preferences[key] = value
        }
        
        fun storeAppState(packageName: String, stateName: String, data: Map<String, String>) {
            appStates[packageName] = Pair(stateName, data)
        }
        
        fun storeInteraction(userQuery: String, assistantResponse: String) {
            interactions.add(Pair(userQuery, assistantResponse))
        }
        
        fun getUserPreferences(): String {
            return preferences.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        }
        
        fun getAppState(packageName: String): String {
            val state = appStates[packageName] ?: return "No state found for $packageName"
            return "App: $packageName\nState: ${state.first}\nData: ${state.second}"
        }
        
        fun getRecentInteractions(): String {
            return interactions.takeLast(2).joinToString("\n\n") { "${it.first}\n${it.second}" }
        }
    }
    
    companion object {
        /**
         * Run the demo from a main method
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val demo = MemorySystemPoCRunner()
            demo.runDemo()
        }
    }
}

/**
 * Main entry point for running the demo
 */
fun main() {
    val demo = MemorySystemPoCRunner()
    demo.runDemo()
}
