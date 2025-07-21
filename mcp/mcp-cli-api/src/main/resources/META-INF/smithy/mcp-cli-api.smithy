$version: "2"

namespace smithy.mcp.cli

use software.amazon.smithy.mcp.bundle.api#BundleMetadata

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

structure TelemetryData {
    @required
    command: String

    @required
    cliVersion: String

    @required
    @default({})
    counters: Counters

    @required
    @default({})
    properties: Properties

    @required
    @default({})
    timings: Timings

    params: String
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

    metadata: BundleMetadata
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

map Counters {
    key: String
    value: PrimitiveLong
}

map Timings {
    key: String
    value: PrimitiveLong
}

map Properties {
    key: String
    value: String
}
