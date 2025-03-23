# Hybrid Memory System Architecture

## Overview

The One Unlimited OS incorporates a sophisticated hybrid memory system that combines multiple storage and retrieval mechanisms to provide AI agents with comprehensive and contextually relevant information. This document outlines the architecture, components, and integration points of this memory system.

## Architecture Components

The memory system is composed of several complementary subsystems, each optimized for different types of information storage and retrieval:

![Memory Architecture](https://mermaid.ink/img/pako:eNp1kk1uwjAQha8y8qqVuAAbkCqVSoUKlqyqRgs3GUJUY0f5KVRVzr46YyhIQJfxvPdm5tnOGbJSIWSQFxsUluXGFtztd4WuSnP_xosbvT2gZXt9qWbTaM4qBdTUbmszXxPsOlrpqmQxAX3oqbdBabCqnZWHuAzYvT-gE2LPwmDXhCCJRCgCGrFo0aOFQ_bx9OVZBC8imvhUUIYTqFSpPZUGwzM6HU1lUzudMEzSbmOJmNHK0H6qazNH7-sSC9ePRiAo4kzIkRhPpZQDwQbhiKcJT_5pR-KNVKU5DL0QOEcXwMRfCXgS9cNpfDWo1MZWl9NuFP_0LvdlzxmS7wnp77Ll8XLc1NpW-N-s_ZJJkJXaoWNTOi1Lti9Q0S-MKbYi2dZYQc5MYS_M1m5QJlNXVZCBq0-waYw6fMMOcr9vbI2qRxvI9AFVYVsaeJAR_rYg-wI9KRkD)

### 1. Vector Database Memory

The vector database component stores memories as embeddings in a high-dimensional vector space, enabling semantic search capabilities that go beyond simple keyword matching.

**Key features:**
- Converts text memories to vector embeddings
- Provides similarity-based retrieval through cosine distance
- Returns memories most semantically relevant to a query
- Supports filtering by agent, tags, and importance

### 2. Knowledge Graph Memory

The knowledge graph component stores memories as entities and relationships, providing context-rich and structured memory access.

**Key features:**
- Represents memories as graph nodes with relationships between them
- Enables traversal of related memories through explicit relationships
- Facilitates reasoning about memory connections and dependencies
- Integrates with the MCP (Model Context Protocol) ecosystem

### 3. Specialized Memory Types

Multiple specialized memory subsystems handle specific types of information:

- **User Preferences Memory**: Stores user preferences and patterns
- **App State Memory**: Tracks navigation paths and application states
- **Interaction Memory**: Records user-agent interactions and their outcomes
- **Device Context Memory**: Maintains information about device state and installed apps

### 4. Short-term Observation System

A short-term memory system captures recent system observations and provides immediate context for agent reasoning.

**Key features:**
- Maintains a rolling buffer of recent UI and system events
- Prioritizes significant events for long-term storage
- Provides immediate context for real-time agent decisions

## Integration and Data Flow

1. **Observation Capture**:
   - The system captures events through the Accessibility Service
   - These observations are stored in the short-term buffer
   - Significant events are identified and promoted to long-term memory

2. **Memory Storage**:
   - New memories are simultaneously stored in the vector database and knowledge graph
   - Specialized memories are stored in their respective subsystems
   - Relationships between memories are explicitly recorded in the knowledge graph

3. **Memory Retrieval**:
   - When an agent needs information, it queries the memory system
   - The vector database provides semantically similar memories
   - The knowledge graph adds related memories through explicit relationships
   - Specialized memory types provide domain-specific information

4. **Memory Consolidation**:
   - Periodically, older memories are consolidated through summarization
   - The consolidation process maintains important information while reducing storage requirements
   - LLM-based summarization creates higher-level abstractions from specific memories

## MCP Integration

The memory system is exposed through the Model Context Protocol (MCP), allowing external systems to utilize its capabilities:

- **Tools**: Store memories, retrieve memories, manage user preferences, etc.
- **Resources**: Access structured memory information through URI templates
- **Server Implementation**: `MemoryMcpServer` provides a standardized interface

## Usage Examples

### Storing a Memory

```kotlin
// Through direct API
memorySystem.storeMemory(
    agentId = "main_assistant",
    content = "User prefers dark mode in all applications",
    importance = 0.8f,
    tags = listOf("preference", "ui")
)

// Through MCP
mcpServerManager.executeTool(
    serverId = "memory_server",
    toolName = "store_memory",
    parameters = mapOf(
        "agent_id" to "main_assistant",
        "content" to "User prefers dark mode in all applications",
        "importance" to 0.8,
        "tags" to listOf("preference", "ui")
    )
)
```

### Retrieving Memories

```kotlin
// Semantic search via direct API
val memories = memorySystem.retrieveRelevantMemories(
    query = "What are the user's UI preferences?",
    limit = 5,
    tags = listOf("preference")
)

// Relationship-based retrieval via MCP
val relatedMemories = mcpServerManager.executeTool(
    serverId = "memory_server",
    toolName = "retrieve_related_memories",
    parameters = mapOf(
        "memory_id" to "mem_1234567890",
        "relationship_type" to "related_to"
    )
)
```

## Future Extensions

The hybrid memory architecture is designed for extensibility and can be enhanced with:

1. **Persistent Storage**: Database integration for long-term memory persistence
2. **Improved Embeddings**: Integration with more sophisticated embedding models
3. **Advanced Knowledge Graph**: Enhanced relationship types and reasoning capabilities
4. **Memory Lifecycle Management**: More sophisticated memory aging and pruning strategies
5. **Multi-user Memory**: Segregation and sharing of memories across users
