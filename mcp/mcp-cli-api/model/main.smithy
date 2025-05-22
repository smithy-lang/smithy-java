$version: "2"

namespace smithy.mcp.cli

structure Config {
    toolBundles: McpBundleConfigs
    defaultRegistry: String
    registries: Registries
    clientConfigs: ClientConfigs
}

map McpBundleConfigs {
    key: String
    value: McpBundleConfig
}

structure McpBundleConfig {
    @required
    name: String

    allowListedTools: ToolNames

    blockListedTools: ToolNames

    @required
    bundleLocation: Location
}

map Registries {
    key: String
    value: RegistryConfig
}

union RegistryConfig {
    javaRegistry: JavaRegistry
}

structure JavaRegistry with [CommonRegistryConfig] {
    jars: Locations
}

@mixin
structure CommonRegistryConfig {
    name: String
}

list Locations {
    member: Location
}

union Location {
    fileLocation: String
}

structure ClientConfig {
    name: String
    filePath: String
}

list ToolNames {
    member: ToolName
}

list ClientConfigs {
    member: ClientConfig
}

string ToolName

structure McpServersClientConfig {
    mcpServers: McpServerConfigs
}

map McpServerConfigs {
    key: String
    value: McpServerConfig
}

structure McpServerConfig {
    command: String
    args: ArgsList
}

list ArgsList {
    member: String
}
