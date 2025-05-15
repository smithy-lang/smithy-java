$version: "2"

namespace smithy.mcp

structure JsonRpcRequest {
    @required
    jsonrpc: String

    @required
    method: String

    @required
    id: Integer

    params: Document
}

structure JsonRpcResponse {
    @required
    jsonrpc: String

    result: Document

    error: JsonRpcErrorResponse

    @required
    id: Integer
}

structure JsonRpcErrorResponse {
    @required
    code: Integer = 0

    message: String

    data: Document
}

@trait(selector: "service")
structure jsonRpc2 {}

@trait(selector: "operation")
structure jsonRpc2Method {
    @required
    method: NonEmptyString
}

@private
@length(min: 1)
string NonEmptyString

@mixin
structure BaseResult {
    @default("2024-11-05")
    protocolVersion: String
}

structure InitializeResult with [BaseResult] {
    @required
    capabilities: Capabilities

    @required
    serverInfo: ServerInfo
}

structure Capabilities {
    logging: Document
    prompts: Prompts
    tools: Tools
}

structure Prompts {
    @default(false)
    listChanged: Boolean
}

structure Resources {
    @default(false)
    subscribe: Boolean
}

structure Tools {
    @default(false)
    listChanged: Boolean
}

structure ServerInfo {
    @required
    name: String

    @required
    version: String
}

structure ListToolsResult {
    tools: ToolInfoList
}

structure ToolInfo {
    @required
    name: String

    description: String

    inputSchema: ToolInputSchema
}

list ToolInfoList {
    member: ToolInfo
}

structure ToolInputSchema {
    @default("object")
    @required
    type: String

    properties: PropertiesMap

    required: StringList

    @required
    additionalProperties: PrimitiveBoolean = false

    @jsonName("$schema")
    schema: String = "http://json-schema.org/draft-07/schema#"
}

map PropertiesMap {
    key: String
    value: PropertyDetails
}

structure PropertyDetails {
    @required
    type: String

    description: String
}

list StringList {
    member: String
}

structure CallToolResult {
    // TODO add others
    content: TextContentList

    @default(false)
    isError: Boolean
}

list TextContentList {
    member: TextContent
}

structure TextContent {
    @required
    @default("text")
    type: String

    text: String
}
