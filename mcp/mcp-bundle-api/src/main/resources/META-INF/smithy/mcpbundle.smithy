$version: "2"

namespace software.amazon.smithy.mcp.bundle.api

use software.amazon.smithy.modelbundle.api#SmithyBundle

union Bundle {
    smithyBundle: SmithyMcpBundle
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
