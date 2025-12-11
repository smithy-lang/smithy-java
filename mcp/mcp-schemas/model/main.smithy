$version: "2"

namespace smithy.mcp

structure JsonRpcRequest {
    @required
    jsonrpc: String

    @required
    method: String

    id: Document

    params: Document
}

structure JsonRpcResponse {
    @required
    jsonrpc: String

    result: Document

    error: JsonRpcErrorResponse

    id: Document
}

structure JsonRpcErrorResponse {
    @required
    code: Integer = 0

    message: String

    data: Document
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

    instructions: String
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

    inputSchema: JsonObjectSchema

    outputSchema: JsonObjectSchema
}

list ToolInfoList {
    member: ToolInfo
}

structure JsonObjectSchema {
    @required
    type: String = "object"

    properties: PropertiesMap

    required: StringList

    additionalProperties: Document

    description: String

    @jsonName("$schema")
    schema: String = "http://json-schema.org/draft-07/schema#"
}

structure JsonArraySchema {
    @required
    type: String = "array"

    /// one of JsonObjectSchema | JsonArraySchema | JsonPrimitiveSchema
    @required
    items: Document

    uniqueItems: PrimitiveBoolean = false

    description: String

    default: Document
}

structure JsonPrimitiveSchema {
    @required
    type: JsonPrimitiveType

    description: String

    @jsonName("const")
    constValue: String

    @jsonName("enum")
    enumValues: JsonSchemaList
}

structure JsonDocumentSchema {
    @required
    type: StringList

    description: String
}

structure JsonOneOfSchema {
    /// List of inline schema variants
    @required
    oneOf: JsonSchemaList

    description: String

    @required
    type: String = "object"
}

list JsonSchemaList {
    /// Each element is a full inline JSON Schema (JsonObjectSchema, etc.)
    member: Document
}

enum JsonPrimitiveType {
    NUMBER = "number"
    STRING = "string"
    BOOLEAN = "boolean"
    NULL = "null"
}

map PropertiesMap {
    key: String

    /// one of JsonObjectSchema | JsonArraySchema | JsonPrimitiveSchema | JsonDocumentSchema
    value: Document
}

list StringList {
    member: String
}

structure CallToolResult {
    // TODO add others
    content: TextContentList

    structuredContent: Document

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

structure ListPromptsResult {
    prompts: PromptInfoList
}

list PromptInfoList {
    member: PromptInfo
}

structure PromptArgument {
    @required
    name: String

    description: String

    required: Boolean
}

list PromptArgumentList {
    member: PromptArgument
}

structure PromptInfo {
    @required
    name: String

    title: String

    description: String

    arguments: PromptArgumentList
}

structure PromptMessageContent {
    @required
    type: PromptMessageContentType = "text"

    text: String
}

enum PromptMessageContentType {
    TEXT = "text"
}

structure PromptMessage {
    @required
    role: String

    @required
    content: PromptMessageContent
}

enum PromptRole {
    USER = "user"
    ASSISTANT = "assistant"
}

list PromptMessageList {
    member: PromptMessage
}

structure GetPromptResult {
    description: String
    messages: PromptMessageList
}

@unstable
@trait(selector: "document")
structure oneOf {
    /// Discriminator field name to add during runtime input adaptation
    @required
    discriminator: String

    /// List of possible variant types
    @required
    members: OneOfMemberList
}

list OneOfMemberList {
    member: OneOfMember
}

structure OneOfMember {
    /// The member name (e.g., "circle", "square")
    @required
    name: String

    /// Target shape ID - uses @idRef for Smithy validation
    @required
    target: OneOfTargetShape
}

/// Shape ID reference for oneOf target - validates it points to a structure
@private
@idRef(failWhenMissing: true, selector: "structure")
string OneOfTargetShape
