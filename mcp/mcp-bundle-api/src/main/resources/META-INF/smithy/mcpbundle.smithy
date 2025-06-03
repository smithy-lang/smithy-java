$version: "2"

namespace software.amazon.smithy.mcp.bundle.api

use software.amazon.smithy.modelbundle.api#SmithyBundle

union Bundle {
    smithyBundle: SmithyMcpBundle
    genericBundle: GenericBundle
}

structure SmithyMcpBundle with [CommonBundleConfig] {
    bundle: SmithyBundle
}

@mixin
structure CommonBundleConfig {
    metadata: BundleMetadata
}

structure BundleMetadata {
    @required
    name: String

    description: String

    version: String
}

structure GenericBundle with [CommonBundleConfig] {
    artifact: GenericArtifact

    install: ExecSpecs

    run: ExecSpec

    /// Whether to invoke the run command directly.
    executeDirectly: PrimitiveBoolean = false
}

list ExecSpecs {
    member: ExecSpec
}

structure ExecSpec {
    executable: String
    args: ArgList
}

list ArgList {
    member: String
}

union GenericArtifact {
    empty: Unit
}
