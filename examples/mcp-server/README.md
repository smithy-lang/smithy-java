## Example: MCP Server

This example contains two newline-delimited JSON-RPC servers using the MCP
standard input/output transport:

- `MCPServerExample` exposes generated Smithy service implementations directly.
- `ProxyMCPExample` starts a Smithy HTTP server on port `8080` and exposes a
  `ProxyService` for it through MCP.

### Usage

To use this example as a template, run the following command with
the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):

```console
smithy init -t mcp-server --url https://github.com/smithy-lang/smithy-java
```

Or

```console
smithy init -t mcp-server --url git@github.com:smithy-lang/smithy-java.git
```

The generated server uses the transport-specific `StdioMcpServer` entry point:

```java
var mcpServer = StdioMcpServer.builder()
        .stdio()
        .name("smithy-mcp-server")
        .addService("employee-mcp", service)
        .build();

mcpServer.start();
mcpServer.awaitCompletion();
```

To compile both implementations and generate a fat JAR from a Smithy Java
checkout, run:

```console
./gradlew :examples:mcp-server:build
```

The fat JAR is written to
`examples/mcp-server/build/libs/mcp-server-<version>-all.jar`. It contains the
generated service code, both example entry points, and the MCP standard
input/output transport.

Run the proxy example from the repository root with:

```console
java -cp examples/mcp-server/build/libs/mcp-server-*-all.jar \
  software.amazon.smithy.java.example.server.mcp.ProxyMCPExample
```

Replace `ProxyMCPExample` with `MCPServerExample` to run the direct service
implementation.

An MCP client can launch the proxy server with a configuration like:

```json
{
  "mcpServers": {
    "smithy-mcp-server": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/smithy-java/examples/mcp-server/build/libs/mcp-server-<version>-all.jar",
        "software.amazon.smithy.java.example.server.mcp.ProxyMCPExample"
      ]
    }
  }
}
```
