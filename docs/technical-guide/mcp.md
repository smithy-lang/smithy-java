# MCP (Model Context Protocol)

> **Last updated:** April 29, 2026

MCP is a JSON-RPC 2.0-based protocol that enables AI/LLM clients (like Claude Desktop, VS Code Copilot) to discover and
invoke "tools" exposed by servers. Smithy-Java's MCP integration bridges Smithy service models to MCP by automatically
converting Smithy operations into MCP tools with JSON Schema descriptions. The system is marked **developer-preview**
(`@SmithyUnstableApi`).

**Source:**
- [`mcp/mcp-server/`](https://github.com/smithy-lang/smithy-java/tree/main/mcp/mcp-server) — MCP server runtime
- [`mcp/mcp-schemas/`](https://github.com/smithy-lang/smithy-java/tree/main/mcp/mcp-schemas) — MCP protocol types
  (Smithy models + generated Java)
- [`smithy-ai-traits/`](https://github.com/smithy-lang/smithy-java/tree/main/smithy-ai-traits) — The `@prompts` trait

## Architecture

```
Smithy Service (with operations)
    ↓ codegen (server mode)
Generated Service + Operation interfaces
    ↓ user implements operations
Service instance
    ↓ registered with McpServer
McpServer (stdio/HTTP transport)
    ↓ converts operations to MCP tools
MCP Client (Claude, VS Code, etc.)
```

### Module Structure

| Module | Purpose |
|--------|---------|
| `mcp-schemas` | Smithy models for MCP protocol types + generated Java code |
| `mcp-server` | MCP server runtime (stdio/HTTP transport, tool dispatch) |
| `smithy-ai-traits` | The `@prompts` Smithy trait definition |
| `aws-mcp-types` | AWS-specific MCP types (PreRequest, AwsServiceMetadata) |

## McpServer

```java
McpServer server = McpServer.builder()
    .stdio()                                    // use System.in/System.out
    .name("my-mcp-server")
    .version("1.0.0")
    .addService("employee-tools", employeeService)  // local Smithy service
    .addService(stdioProxy)                          // remote MCP server proxy
    .toolFilter((serverName, toolName) -> true)      // optional filtering
    .build();

server.start();
server.awaitCompletion();
```

- Reads JSON-RPC messages line-by-line from an `InputStream` (stdio transport)
- Writes responses to an `OutputStream` with newline delimiters
- Delegates all protocol logic to `McpService`
- Supports hot-reloading: `addNewService()` and `addNewProxy()` with `tools/list_changed` notifications

## McpService — The Protocol Engine

Handles all MCP protocol methods:

| Method | Description |
|--------|-------------|
| `initialize` | Returns capabilities, server info, protocol version negotiation |
| `ping` | Returns empty result |
| `tools/list` | Returns all registered tools with JSON Schema |
| `tools/call` | Invokes a tool (local operation or proxy) |
| `prompts/list` | Returns all registered prompts |
| `prompts/get` | Returns prompt content with template substitution |

### Smithy-to-JSON-Schema Conversion

The most complex part. Maps Smithy types to JSON Schema:

| Smithy Type | JSON Schema |
|-------------|-------------|
| STRING, ENUM, BLOB, TIMESTAMP | `"string"` |
| BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE | `"number"` |
| BOOLEAN | `"boolean"` |
| STRUCTURE | `"object"` with properties |
| LIST, SET | `"array"` with items |
| MAP | `"object"` with additionalProperties |
| UNION | `oneOf` with wrapped member variants |
| DOCUMENT | `["string","number","boolean","object","array","null"]` |

### Input/Output Adaptation

Handles type mismatches between JSON (what LLMs send) and Smithy types:
- `adaptDocument()` — Converts string→BigDecimal, string→BigInteger, Base64 string→Blob, various timestamp formats
- `adaptOutputDocument()` — Reverse: BigDecimal→string, Blob→Base64, discriminated union→wrapper format

### Protocol Version Handling

```java
public abstract sealed class ProtocolVersion {
    v2024_11_05  // Original version
    v2025_03_26  // Adds annotations support (default)
    v2025_06_18  // Adds outputSchema support
}
```

Features are gated by protocol version — `outputSchema` only sent for ≥ 2025-06-18, `annotations` for ≥ 2025-03-26.

## Proxy Support

### StdioProxy

Launches an external MCP server as a child process, communicates via stdin/stdout:

```java
StdioProxy proxy = StdioProxy.builder()
    .command("npx")
    .arguments(List.of("some-mcp-server"))
    .name("remote-tools")
    .build();
```

Uses `ConcurrentHashMap<String, CompletableFuture<JsonRpcResponse>>` for request/response correlation. Virtual threads
for response reading.

### HttpMcpProxy

Sends JSON-RPC requests as HTTP POST to a remote endpoint:

```java
HttpMcpProxy proxy = HttpMcpProxy.builder()
    .endpoint("https://mcp.example.com")
    .signer(sigv4Signer)
    .timeout(Duration.ofMinutes(5))
    .build();
```

Supports SSE response parsing, session management via `Mcp-Session-Id` header, and custom `Signer` for authentication.

## The @prompts Trait

Defined in `smithy-ai-traits` (namespace `smithy.ai`):

```smithy
@trait(selector: ":is(service, operation)")
map prompts {
    key: PromptName
    value: PromptTemplateDefinition
}

structure PromptTemplateDefinition {
    @required description: String
    @required template: String
    arguments: ArgumentShape    // @idRef pointing to a structure
    preferWhen: String
}
```

Template substitution uses `{{placeholder}}` pattern. `preferWhen` is appended as ".Tool preference: ..." suffix.

## The @oneOf Trait

Applied to Smithy `document` shapes for discriminated unions:

```smithy
@oneOf(discriminator: "__type", members: [{name: "circle", target: Circle}])
document ShapeWithOneOf
```

The MCP server converts between:
- **MCP format** (wrapper): `{"circle": {"radius": 5}}`
- **Smithy format** (discriminated): `{"__type": "smithy.example#Circle", "radius": 5}`

## Building MCP Services

### smithy-build.json Configuration

```json
{
  "plugins": {
    "java-codegen": {
      "service": "smithy.example#EmployeeService",
      "namespace": "com.example.mcp",
      "runtimeTraits": ["smithy.api#documentation", "smithy.api#examples", "smithy.ai#prompts"],
      "modes": ["server"]
    }
  }
}
```

The `runtimeTraits` array is critical — it tells codegen to preserve `@documentation` (for tool descriptions) and
`@prompts` (for prompt templates) at runtime.

### End-to-End Flow

1. Define Smithy model with service, operations, and optional `@prompts` trait
2. Run codegen in `server` mode → generates service stubs and operation interfaces
3. Implement operations
4. Build the service using the generated staged builder
5. Create `McpServer` with `addService()` or proxy support
6. Client sends `tools/list` → server converts each operation to a `ToolInfo` with JSON Schema
7. Client sends `tools/call` → server deserializes arguments, invokes operation, serializes output

### Proxy Aggregation

A single MCP server can aggregate tools from multiple sources:

```java
McpServer.builder()
    .stdio()
    .addService("local-tools", localService)           // local Smithy service
    .addService(StdioProxy.builder()...build())         // subprocess MCP server
    .addService(HttpMcpProxy.builder()...build())       // remote HTTP MCP server
    .build();
```

## Key Classes Summary

| Class | Role |
|---|---|
| `McpServer` | Transport layer (stdio I/O), lifecycle management |
| `McpServerBuilder` | Fluent builder for McpServer |
| `McpService` | Protocol engine — handles all MCP methods, schema conversion |
| `McpServerProxy` | Abstract base for remote MCP server proxies |
| `StdioProxy` | Subprocess-based MCP proxy (stdin/stdout) |
| `HttpMcpProxy` | HTTP-based MCP proxy with SSE and session management |
| `Prompt` | Prompt handling with template substitution |
| `PromptLoader` | Loads prompts from `@prompts` traits on Smithy services |
| `ToolFilter` | Interface for filtering which tools are exposed |
| `McpMetricsObserver` | Metrics collection interface |
| `ProtocolVersion` | Sealed class for MCP protocol version negotiation |
