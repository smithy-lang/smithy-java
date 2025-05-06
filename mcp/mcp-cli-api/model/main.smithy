$version: "2"

namespace smithy.mcp.cli

use software.amazon.smithy.modelbundle.api#Bundle

structure Config {
    toolBundles: ToolBundleConfigs
}

map ToolBundleConfigs {
    key: String
    value: ToolBundleConfig
}

union ToolBundleConfig {
    smithyModeled: SmithyModeledToolBundleConfig
    genericConfig: GenericToolBundleConfig
}

@mixin
structure CommonToolConfig {
    name: String
    allowListedTools: ToolNames
    blockListedTools: ToolNames
}

structure SmithyModeledToolBundleConfig with [CommonToolConfig] {
    bundlePlugins: BundlePlugins

    allowListedTools: ToolNames

    blockListedTools: ToolNames

    serviceDescriptor: Bundle

    // TODO separate this into another location and just reference it here.
}

structure GenericToolBundleConfig with [CommonToolConfig] {
    config: Document
}

list BundlePlugins {
    member: BundlePlugin
}

structure BundlePlugin {
    name: String
    jars: FilePaths
}

list ToolNames {
    member: ToolName
}

list FilePaths {
    member: FilePath
}

string FilePath

string ToolName
