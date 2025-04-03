## Example: MCP Server

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

To generate a fat jar which contains all the dependencies required to run
a [Model Context Protocol](https://modelcontextprotocol.io/) (
MCP) [StdIO](https://modelcontextprotocol.io/docs/concepts/transports#standard-input%2Foutput-stdio) server,
run the following from the root of the project:

```console
gradle build
```

This will generate a fat JAR file at `build/libs/mcp-server-0.0.1-all.jar`. This artifact includes all the necessary
code to create an MCP server that uses the StdIO transport.

There are two example implementations included:

* `MCPServerExample` : Demonstrates how to build an MCP server by modeling tools as Smithy APIs.

* `ProxyMCPExample` : Shows how to create a Proxy MCP Server for any Smithy service. In this example, a Smithy Java
  server is started on port 8080, and the MCP server proxies requests to it.

You can run the Proxy MCP Server using the following command:

```
java -cp mcp-server-0.0.1-all.jar software.amazon.smithy.java.example.server.mcp.ProxyMCPExample
```

To run the direct MCP server example instead, simply replace `ProxyMCPExample` with `MCPServerExample`.

Here's how you might configure the MCP client to invoke the proxy server:

```json
{
  "mcpServers": {
    "smithy-mcp-server": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/build/libs/mcp-server-0.0.1-all.jar",
        "software.amazon.smithy.java.example.server.mcp.ProxyMCPExample"
      ]
    }
  }
}
```




