$version: "2"

namespace smithy.mcp.toolassistant

/// This service provides methods to search MCP Tools and install MCP servers.
/// Be aware that tools installed using InstallTool are available as part of the ToolAssistant MCP server and the MCP serverId returned from search tool needs to be ignored while tool calling.
service ToolAssistant {
    operations: [
        SearchTools
        InstallTool
    ]
}

/// You MUST use the SearchTools MCP tool whenever the current toolset is not sufficient to complete the task.
/// Do not attempt to proceed with incomplete or inadequate tools.
/// SearchTools returns a ranked list of tools relevant to the task description—always use it to expand your capabilities.
/// If any required tool is missing, immediately install it using the InstallTool API.
/// This process can and should be repeated: keep invoking SearchTools and InstallTool until you have the tools needed to complete the task successfully.
/// Do not rely solely on the preloaded tools. Tool discovery and installation is a core part of your workflow.
operation SearchTools {
    input := {
        /// Generate a concise tool description based on the current dialogue context.
        ///
        /// Focus on the user’s intent, including specific actions, platforms, or systems mentioned.
        ///
        /// Include concrete nouns, URLs, acronyms, or product names that help identify the task.
        ///
        /// The goal is to create a search query that helps SearchTools return the most relevant tools.
        ///
        /// Avoid vague or generic phrasing—be as specific and task-oriented as possible.
        ///
        // Example
        ///
        /// Dialogue: "Hi, can you help me create a code review? I use code.amazon.com"
        ///
        /// Tool Description: "Create a code review on code.amazon.com"
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
    /// Name of this tool
    @required
    toolName: String
}

/// Install a new MCP Tool for local use.
/// Be aware that tools installed using InstallTool are available as part of the ToolAssistant MCP server.
operation InstallTool {
    input := {
        /// The name of the MCP tool to install
        @required
        toolName: String
    }

    output := {
        message: String
    }
}
