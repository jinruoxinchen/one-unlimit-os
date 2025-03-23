# Model Context Protocol (MCP) Architecture in One Unlimited OS

## Overview

The Model Context Protocol (MCP) architecture in One Unlimited OS creates a flexible, extensible framework for integrating external capabilities with AI agents. This document describes the design and implementation of the MCP system, which enables dynamic tool discovery, resource access, and structured AI interactions.

## Key Components

### 1. MCP Server Framework

The MCP Server framework defines a consistent interface for external capability providers:

- **McpServer Interface**: Core interface defining how MCP servers expose tools and resources
- **HttpMcpServer**: Base implementation for HTTP-based MCP servers
- **McpServerManager**: Central manager for MCP server registration, connection, and tool discovery
- **McpToolAdapter**: Adapter that converts MCP tools to the standard Tool interface

### 2. MCP Data Models

- **McpTool**: Represents a tool provided by an MCP server
- **McpResource**: Represents a resource provided by an MCP server
- **McpParameterSchema**: Defines the schema for tool parameters
- **McpToolResponse**: Standard response format for tool executions

### 3. MCP Implementation Examples

- **WeChatMcpServer**: MCP server for WeChat integration
  - Messaging tools (sending messages, reading conversations)
  - Contact management
  - Group chat tools
  - QR code handling

- **PersonalInfoMcpServer**: MCP server for personal information management
  - Calendar tools (listing events, creating events)
  - Contact tools (listing contacts, retrieving contact details)
  - Reminder tools (listing reminders, creating reminders, marking completion)

### 4. Integration with Existing Systems

- **ToolRegistry**: Extended to discover and register MCP tools
- **AgentManager**: Updated to initialize and manage MCP servers
- **ClaudeApiClient**: Enhanced to support tool calling and structured prompts

## Implementation Details

### MCP Server Lifecycle

1. **Registration**: MCP servers are registered with McpServerManager
2. **Connection**: Servers establish connections (HTTP or direct)
3. **Tool Discovery**: Available tools are discovered and registered
4. **Tool Execution**: Tools are executed through standard Tool interface
5. **Disconnection**: Server connections are properly closed when no longer needed

### Tool Adaptation Process

1. MCP tools are discovered from connected servers
2. Each MCP tool is wrapped in a McpToolAdapter that implements the Tool interface
3. Adapted tools are registered in the ToolRegistry
4. Tools are made available to agents based on permission settings

### LLM Integration

1. Available tools are included in prompts to the Claude API
2. Structured tool definitions guide the LLM in proper tool use
3. Tool calls in LLM responses are parsed and executed
4. Results are returned to the LLM for continued conversation

## Security and Permission Model

The MCP architecture implements several security measures:

1. **Tool Permission System**: Agents can only access tools they have been granted permissions for
2. **Server Authentication**: Secure communication with MCP servers
3. **Parameter Validation**: Strict validation of tool parameters before execution
4. **Error Handling**: Comprehensive error handling for failed tool executions

## Future Enhancements

1. **Enhanced Tool Discovery**: Dynamic addition and removal of tools at runtime
2. **Tool Composition**: Ability to compose multiple tools into workflows
3. **Caching and Optimization**: Performance improvements for frequently used tools
4. **Cross-Tool Context**: Maintaining context across multiple tool calls
5. **Expanded MCP Server Library**: Development of additional specialized MCP servers

## Conclusion

The MCP architecture provides a powerful extension mechanism for One Unlimited OS, enabling AI agents to leverage a wide range of external capabilities. This modular approach allows for continuous expansion of the system's capabilities without modifying the core agent architecture.
