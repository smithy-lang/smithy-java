$version: "2"

namespace smithy.mcp.cli

structure Config {
    toolBundles: McpBundleConfigs

    defaultRegistry: String

    registries: Registries

    @default([])
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

    @required
    bundleLocation: Location

    @default(false)
    local: PrimitiveBoolean

    description: String
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

structure SmithyModeledBundleConfig with [CommonToolConfig] {}

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

    @default(false)
    isDefault: Boolean

    disabled: Boolean
}

list ToolNames {
    member: ToolName
}

@uniqueItems
list ClientConfigs {
    member: ClientConfig
}

string ToolName

structure McpServersClientConfig {
    mcpServers: McpServerConfigs
}

map McpServerConfigs {
    key: String
    value: Document
}

structure McpServerConfig {
    command: String
    args: ArgsList
    env: EnvVars
}

list ArgsList {
    member: String
}

map EnvVars {
    key: String
    value: String
}
