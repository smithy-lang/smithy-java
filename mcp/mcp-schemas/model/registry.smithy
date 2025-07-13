$version: "2"

namespace smithy.mcp.registry

/// This service provides methods to search MCP Tools and install MCP servers. You can get a list of MCP tools that are most appropriate
/// for the given task with SearchTools. If the given tool is not already available you can install using the InstallTool api.
/// Be aware that tools installed using InstallTool are available as part of the ToolAssistant MCP server and the MCP serverId returned from search tool needs to be ignored while tool calling.
service McpRegistry {
    operations: [
        SearchTools
        InstallTool
    ]
}

/// Search MCP Tools that can help to perform a current task or answer a query. This can be invoked multiple times
operation SearchTools {
    input := {
        /// Tool Description based on the dialogue context. Include relevant information like urls, nouns, acronyms etc.
        /// Example dialogue:
        /// User: Hi, can you help me create a code review. I use code.amazon.com
        /// Example Tool Description : "Create a code review on code.amazon.com"
        toolDescription: String

        /// Number of tools to return based on relevance in descending order of relevance. If not specified, the default is 1
        @default(1)
        numberOfTools: Integer
    }

    output := {
        /// List of MCP tools most relevant for the query, sorted by order of relevance,
        /// the first tool being the most relevant.
        @required
        tools: Tools
    }
}

list Tools {
    member: Tool
}

structure Tool {
    /// Id of the MCP server this Tool belongs to
    @required
    serverId: String

    /// Name of the Tool
    toolName: String
}

structure ServerEntry {
    description: String
}

/// Install a new MCP Tool for local use.
/// Be aware that tools installed using InstallTool are available as part of the ToolAssistant MCP server and the MCP serverId returned from search tool needs to be ignored while tool calling.
operation InstallTool {
    input := {
        /// The name of the MCP server to install
        @required
        tool: Tool
    }

    output := {
        message: String
    }
}
