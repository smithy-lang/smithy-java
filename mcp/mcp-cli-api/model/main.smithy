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

union McpBundleConfig {
    smithyModeled: SmithyModeledBundleConfig
    genericConfig: GenericToolBundleConfig
}

@mixin
structure CommonToolConfig {
    name: String
    allowListedTools: ToolNames
    blockListedTools: ToolNames
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

structure SmithyModeledBundleConfig with [CommonToolConfig] {
    @required
    bundleLocation: Location
}

list Locations {
    member: Location
}

union Location {
    fileLocation: String
}

structure GenericToolBundleConfig with [CommonToolConfig] {
    config: Document
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
