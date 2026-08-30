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
}
```

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

Use `McpInterceptor` to observe or replace immutable calls and outcomes. Custom
method implementations use `McpExtensionMethod<P>` and are registered with
`McpEngine.Builder.addExtension`. Its outbound `encode` operation defaults to
`UnsupportedOperationException`, so inbound-only extensions implement only
decoding and execution.

The module supports protocol revisions through `2026-07-28`. Run the official
Model Context Protocol conformance scenarios with:

```console
./gradlew :mcp:mcp-server:conformance
```

The conformance task requires Node.js and invokes the pinned
`@modelcontextprotocol/conformance` package.
