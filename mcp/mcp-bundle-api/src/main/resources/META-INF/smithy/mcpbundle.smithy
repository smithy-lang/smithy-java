$version: "2"

namespace software.amazon.smithy.mcp.bundle.api

use software.amazon.smithy.modelbundle.api#SmithyBundle

union Bundle {
    smithyBundle: SmithyBundle
}

structure BundleMetadata {
    @required
    name: String

    @required
    description: String

    version: String
}
