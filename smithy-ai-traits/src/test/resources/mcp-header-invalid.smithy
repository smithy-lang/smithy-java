$version: "2"

namespace smithy.ai.test

use smithy.ai#mcpHeader

structure InvalidMcpHeaderInput {
    @mcpHeader("tenant")
    value: Integer
}
