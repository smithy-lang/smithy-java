/**
 * Extensible MCP execution, protocol, client, and transport support for exposing
 * Smithy services as tools.
 *
 * <p>{@link software.amazon.smithy.java.mcp.server.McpEngine} is the blocking,
 * transport-independent core. Standard calls and outcomes use sealed typed
 * hierarchies. Custom methods are added with
 * {@link software.amazon.smithy.java.mcp.server.McpExtensionMethod}, and external
 * protocol versions implement
 * {@link software.amazon.smithy.java.mcp.server.ExtensionMcpProtocol} directly or
 * through {@link software.amazon.smithy.java.mcp.server.McpProtocolProvider}.
 * Stdio and HTTP adapters own transport concerns and concurrency.
 *
 * <p>This package is under development and is not intended for use in production.
 */
@SmithyUnstableApi
package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;
