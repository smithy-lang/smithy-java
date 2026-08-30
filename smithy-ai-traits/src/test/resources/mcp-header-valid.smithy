$version: "2"

namespace smithy.ai.test

use smithy.ai#mcpHeader

structure ValidMcpHeaderInput {
    @mcpHeader("tenant")
    value: String
}
