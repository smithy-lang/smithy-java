$version: "2"

namespace smithy.mcp.cli

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

// TODO fix this once external type Schemas are fixed
structure Bundle {
    /// unique identifier for the configuration type. used to resolve the appropriate Bundler.
    @required
    configType: String

    /// fully-qualified ShapeId of the service
    @required
    serviceName: String

    /// Bundle-specific configuration. If this bundle does not require configuration, this
    /// field may be omitted.
    config: Document

    /// model that describes the service. The service given in `serviceName` must be present.
    @required
    model: Model

    /// model describing the generic arguments that must be present in every request. If this
    /// bundle does not require generic arguments, this field may be omitted.
    requestArguments: GenericArguments
}

union Model {
    smithyModel: String
}

structure GenericArguments {
    @required
    identifier: String

    @required
    model: Model
}
