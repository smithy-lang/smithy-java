$version: "2"

namespace smithy.ai

/// Mirrors a string member into an MCP HTTP `Mcp-Param-*` header.
///
/// The trait value is the suffix appended to `Mcp-Param-`. Servers validate
/// that the decoded header value matches the corresponding JSON body member.
@unstable
@trait(selector: ":is(member)")
@pattern("^[A-Za-z0-9][A-Za-z0-9_-]*$")
string mcpHeader
