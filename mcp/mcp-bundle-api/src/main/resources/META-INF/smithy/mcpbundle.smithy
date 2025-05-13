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
    binary: Blob
    install: String
    command: String
    args: StringList
}

list StringList {
    member: String
}
