$version: "2"

namespace smithy.mcp.registry

/// This service provides methods to list and install MCP servers. You can get a list of available servers with
/// ListServers. Use a server from that method to install a new server with the InstallServer API.
service McpRegistry {
    operations: [
        ListServers
        InstallServer
    ]
}

/// List the available MCP servers that you can install
operation ListServers {
    output := {
        /// A map of server name to details about that server
        @required
        servers: ServerMap
    }
}

map ServerMap {
    key: String
    value: ServerEntry
}

structure ServerEntry {
    description: String
}

/// Install a new MCP server for local use.
operation InstallServer {
    input := {
        /// The name of the MCP server to install
        @required
        serverName: String
    }
}
