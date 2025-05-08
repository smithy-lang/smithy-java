$version: "2"

namespace software.amazon.smithy.mcp.bundle.api

use software.amazon.smithy.modelbundle.api#SmithyBundle

union Bundle {
    smithyBundle: SmithyBundle
    codeRepoBundle: CodeRepoBundle
}

structure BundleMetadata {
    @required
    name: String

    @required
    description: String

    version: String
}

structure CodeRepoBundle {
    // The URL of this MCP server's code repository
    @required
    codeRepoUrl: String
}