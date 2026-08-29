## mcp-server

> [!WARNING]
> This is a developer-preview module and may contain bugs. No guarantee is made about API stability.
> This module is not recommended for production use.

Provides Model Context Protocol (MCP) server support for Smithy Java, enabling MCP server generation from Smithy models.

## Creating a standard input/output server

Generated Smithy services can be exposed directly:

```java
var mcpServer = StdioMcpServer.builder()
        .stdio()
        .name("employee-server")
        .version("1.0.0")
        .addService("employees", employeeService)
        .build();

mcpServer.start();
mcpServer.awaitCompletion();
```

For applications that need to share execution across transports, construct the
transport-independent engine separately:

```java
var engine = McpEngine.builder()
        .name("employee-server")
        .addService("employees", employeeService)
        .build();

var stdioServer = StdioMcpServer.builder()
        .stdio()
        .engine(engine)
        .build();
```

Builder-managed services and a prebuilt engine are mutually exclusive.

## Architecture and extension points

The implementation is split into a blocking, transport-independent `McpEngine`,
typed sealed `McpCall` and `McpOutcome` hierarchies, declarative per-version
protocol profiles, an immutable-snapshot source aggregator, and transport adapters:

- `StdioMcpServer` exposes an engine over newline-delimited JSON-RPC and executes
  requests on virtual threads.
- `McpHttpHandler` adapts decoded Streamable HTTP requests.
- `HttpMcpClient` and `StdioMcpClient` are blocking remote clients intended to
  run naturally on virtual threads.
- `McpExtensionMethod` adds typed custom methods without modifying the built-in
  protocol dispatch.
- `ExtensionMcpProtocol` is the open branch of the sealed `McpProtocol`
  hierarchy for externally implemented protocol versions.

Unsupported operations default to JSON-RPC method-not-found responses. A new
built-in protocol revision is added as one immutable method/feature declaration,
and the exhaustive version switch makes an incomplete registration fail at compile
time.

## Adding a protocol

Implement `ExtensionMcpProtocol` and override only the behavior that differs from
the defaults:

```java
public final class FutureProtocol implements ExtensionMcpProtocol {
    private static final McpProtocolId ID = McpProtocolId.of("2099-01-01");

    @Override
    public McpProtocolId id() {
        return ID;
    }

    @Override
    public Set<McpMethod.Standard> supportedMethods() {
        return Set.of(
                McpMethod.Standard.INITIALIZE,
                McpMethod.Standard.PING,
                McpMethod.Standard.TOOLS_LIST,
                McpMethod.Standard.TOOLS_CALL);
    }

    @Override
    public McpProtocolFeatures features() {
        return new McpProtocolFeatures(true, true, false, false, false);
    }

    @Override
    public int initializationPriority() {
        return 100;
    }
}
```

`initializationPriority` is considered only for stateful protocols that support
`initialize`. Higher priorities are preferred when a client requests an unknown
or stateless version. Equal highest priorities fail engine construction rather
than making fallback depend on registration order.

Register it directly:

```java
var engine = McpEngine.builder()
        .addProtocol(new FutureProtocol())
        .build();
```

Or publish it through Java's service-provider mechanism:

```java
public final class FutureProtocolProvider implements McpProtocolProvider {
    @Override
    public Collection<? extends ExtensionMcpProtocol> protocols() {
        return List.of(new FutureProtocol());
    }
}
```

Register the provider class in:

```text
META-INF/services/software.amazon.smithy.java.mcp.server.McpProtocolProvider
```

Built-in protocols, discovered providers, and builder registrations share one
immutable registry. Duplicate identifiers fail engine construction. This ensures
that upgrading to a release that implements a previously external protocol does
not silently change behavior. Use `overrideProtocol` only when replacement is
intentional:

```java
var engine = McpEngine.builder()
        .overrideProtocol(new FutureProtocol())
        .build();
```

`discoverProtocols(false)` disables service-provider discovery. Programmatically
registered protocols remain enabled.

## Remote pagination

Remote tool and prompt listings are lazy. `McpRemoteClient.listTools()` and
`listPrompts()` return an `McpPage` containing the current items and an optional
continuation. Fetching the continuation performs exactly one additional upstream
request:

```java
var page = remoteClient.listTools();
process(page.items());

while (page.nextPage().isPresent()) {
    page = page.nextPage().orElseThrow().fetch();
    process(page.items());
}
```

This makes aggregation explicit for direct client users. When a remote client is
attached to an MCP engine, the engine translates each continuation into an opaque
downstream `nextCursor`. It does not eagerly drain the remote listing. Descriptors
from pages already requested are retained for tool and prompt dispatch, while a
fresh listing still starts from the cached first page. This favors availability
over immediate removal: if an upstream deletes an item from a later page, the
previously advertised descriptor can remain dispatchable until pagination reaches
and reconciles that page again.

`ToolFilter` is enforced for both `tools/list` and `tools/call`; a tool hidden
from discovery cannot be invoked by name through the same engine.

The engine forwards the active protocol to remote listings. Stateless requests
include their required `_meta` fields on every page, so modern-only upstream
servers do not depend on a legacy initialization handshake. If a remote does not
support stateless discovery, the engine initializes that remote independently
using the proxy server's identity and the highest-priority compatible stateful
protocol.

## HTTP parameter headers

Annotate a Smithy input member with `smithy.ai#mcpHeader` to mirror that value in
an `Mcp-Param-*` HTTP header. The trait must be retained as a Java runtime trait
when generating the service:

```json
{
  "runtimeTraits": [
    "smithy.ai#mcpHeader"
  ]
}
```

The MCP integration and conformance build include this setting. Projects invoking
Smithy Java code generation directly must add it to their codegen settings.

## Cache hints

Stateless cacheable results default to `ttlMs: 0` and `cacheScope: "private"`.
Configure a different default or override individual methods with an immutable
cache policy:

```java
var cachePolicy = McpCachePolicy.builder()
        .hint(
                McpMethod.Standard.TOOLS_LIST,
                new McpCacheHint(30_000, McpCacheScope.PUBLIC))
        .build();

var engine = McpEngine.builder()
        .cachePolicy(cachePolicy)
        .build();
```

Use `McpInterceptor` to observe or replace immutable calls and outcomes. To
reject a call with a client-visible JSON-RPC error, throw `McpProtocolException`
from an interceptor hook. Other runtime exceptions are logged and returned as a
sanitized `-32603 Internal error`.

Custom method implementations use `McpExtensionMethod<P>` and are registered
with `McpEngine.Builder.addExtension`. Its outbound `encode` operation defaults
to `UnsupportedOperationException`, so inbound-only extensions implement only
decoding and execution.

The module supports protocol revisions through `2026-07-28`. Run the official
Model Context Protocol conformance scenarios with:

```console
./gradlew :mcp:mcp-server:conformance
```

The conformance task requires Node.js and invokes the pinned
`@modelcontextprotocol/conformance` package.
