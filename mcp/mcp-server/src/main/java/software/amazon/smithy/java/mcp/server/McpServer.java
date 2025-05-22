/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.CallToolResult;
import software.amazon.smithy.java.mcp.model.Capabilities;
import software.amazon.smithy.java.mcp.model.InitializeResult;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PropertyDetails;
import software.amazon.smithy.java.mcp.model.ServerInfo;
import software.amazon.smithy.java.mcp.model.TextContent;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.mcp.model.ToolInputSchema;
import software.amazon.smithy.java.mcp.model.Tools;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class McpServer implements Server {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpServer.class);

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private final Map<String, Tool> tools;
    private final Thread listener;
    private final InputStream is;
    private final OutputStream os;
    private final String name;
    private final List<McpServerProxy> proxies;

    McpServer(McpServerBuilder builder) {
        this.tools = createTools(builder.serviceList);
        this.is = builder.is;
        this.os = builder.os;
        this.name = builder.name;
        this.proxies = new ArrayList<>(builder.proxyList);
        this.listener = new Thread(() -> {
            try {
                this.listen();
            } catch (Exception e) {
                LOG.error("Error handling request", e);
            }
        });
        listener.setName("stdio-dispatcher");
        listener.setDaemon(true);
    }

    private void listen() {
        var scan = new Scanner(is, StandardCharsets.UTF_8);
        while (scan.hasNextLine()) {
            var line = scan.nextLine();
            try {
                var jsonRequest = CODEC.deserializeShape(line, JsonRpcRequest.builder());
                handleRequest(jsonRequest, line);
            } catch (Exception e) {
                LOG.error("Error decoding request", e);
            }
        }
    }

    private void handleRequest(JsonRpcRequest req, String rawRequest) {
        try {
            switch (req.getMethod()) {
                case "initialize" -> writeResponse(req.getId(),
                        InitializeResult.builder()
                                .capabilities(Capabilities.builder()
                                        .tools(Tools.builder().listChanged(false).build())
                                        .build())
                                .serverInfo(ServerInfo.builder()
                                        .name(name)
                                        .version("1.0.0")
                                        .build())
                                .build());
                case "tools/list" -> writeResponse(req.getId(),
                        ListToolsResult.builder().tools(tools.values().stream().map(Tool::toolInfo).toList()).build());
                case "tools/call" -> {
                    var operationName = req.getParams().getMember("name").asString();
                    var tool = tools.get(operationName);

                    // Check if this tool should be dispatched to a proxy
                    if (tool.proxy() != null) {
                        // Get response asynchronously
                        // Pass through the response directly
                        tool.proxy().rpc(req).thenAccept(this::writeResponse).exceptionally(ex -> {
                            LOG.error("Error from proxy RPC", ex);
                            internalError(req, new RuntimeException("Proxy error: " + ex.getMessage(), ex));
                            return null;
                        });

                        // Don't send a response here as it will be sent when the future completes
                        return;
                    } else {
                        // Handle locally
                        var operation = tool.operation();
                        var input = req.getParams()
                                .getMember("arguments")
                                .asShape(operation.getApiOperation().inputBuilder());
                        var output = operation.function().apply(input, null);
                        var result = CallToolResult.builder()
                                .content(List.of(TextContent.builder()
                                        .text(CODEC.serializeToString((SerializableShape) output))
                                        .build()))
                                .build();
                        writeResponse(req.getId(), result);
                    }
                }
                default -> {
                    //For now don't do anything
                }
            }
        } catch (Exception e) {
            internalError(req, e);
        }
    }

    private void writeResponse(int id, SerializableStruct value) {
        writeResponse(JsonRpcResponse.builder()
                .id(id)
                .result(Document.of(value))
                .jsonrpc("2.0")
                .build());
    }

    private void writeResponse(JsonRpcResponse response) {
        synchronized (this) {
            try {
                os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                os.write('\n');
                os.flush();
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
            }
        }
    }

    private void internalError(JsonRpcRequest req, Exception exception) {
        String s;
        try (var sw = new StringWriter();
                var pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
            s = sw.toString().replace("\n", "| ");
        } catch (Exception e) {
            LOG.error("Error encoding response", e);
            throw new RuntimeException(e);
        }

        var error = JsonRpcErrorResponse.builder()
                .code(500)
                .message(s)
                .build();
        var response = JsonRpcResponse.builder()
                .id(req.getId())
                .error(error)
                .jsonrpc("2.0")
                .build();
        synchronized (this) {
            try {
                os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
            }
        }
    }

    private static Map<String, Tool> createTools(List<Service> serviceList) {
        var tools = new HashMap<String, Tool>();
        for (Service service : serviceList) {
            var serviceName = service.schema().id().getName();
            for (var operation : service.getAllOperations()) {
                var operationName = operation.name();
                Schema schema = operation.getApiOperation().schema();
                var toolInfo = ToolInfo.builder()
                        .name(operationName)
                        .description(createDescription(serviceName,
                                operationName,
                                schema))
                        .inputSchema(createInputSchema(operation.getApiOperation().inputSchema()))
                        .build();
                tools.put(operationName, new Tool(toolInfo, operation));
            }
        }
        return tools;
    }

    private static ToolInputSchema createInputSchema(Schema schema) {
        var properties = new HashMap<String, PropertyDetails>();
        var requiredProperties = new ArrayList<String>();
        for (var member : schema.members()) {
            var name = member.memberName();
            if (member.hasTrait(TraitKey.REQUIRED_TRAIT)) {
                requiredProperties.add(name);
            }
            // adapt types to json-schema types
            // https://json-schema.org/draft-07/schema#
            var type = switch (member.type()) {
                case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE -> "number";
                case ENUM, BLOB -> "string";
                case LIST, SET -> "array";
                case TIMESTAMP -> resolveTimestampType(member.memberTarget());
                case MAP, DOCUMENT, STRUCTURE, UNION -> "object";
                case STRING, BIG_DECIMAL, BIG_INTEGER -> "string";
                case BOOLEAN -> "boolean";
                default -> throw new RuntimeException("unsupported type: " + member.type() + "on member " + member);
            };
            var details = PropertyDetails.builder().typeMember(type);
            var documentation = member.getTrait(TraitKey.DOCUMENTATION_TRAIT);
            if (documentation != null) {
                details.description(documentation.getValue());
            }
            properties.put(name, details.build());
        }
        return ToolInputSchema.builder().properties(properties).required(requiredProperties).build();
    }

    private static String resolveTimestampType(Schema schema) {
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (trait == null) {
            // default is epoch-seconds
            return "number";
        }
        return switch (trait.getFormat()) {
            case EPOCH_SECONDS -> "number";
            case DATE_TIME, HTTP_DATE -> "string";
            default -> throw new RuntimeException("unknown timestamp format: " + trait.getFormat());
        };
    }

    private static String createDescription(
            String serviceName,
            String operationName,
            Schema schema
    ) {
        var documentationTrait = schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        if (documentationTrait != null) {
            return "This tool invokes %s API of %s.".formatted(operationName, serviceName) +
                    documentationTrait.getValue();
        } else {
            return "This tool invokes %s API of %s.".formatted(operationName, serviceName);
        }
    }

    @Override
    public void start() {
        // Start all proxies first
        for (McpServerProxy proxy : proxies) {
            proxy.start();
            proxy.initialize(this::writeResponse);

            // Get the tools from this proxy
            try {
                List<ToolInfo> proxyTools = proxy.listTools();

                // Add each tool to our tool map
                for (var toolInfo : proxyTools) {
                    tools.put(toolInfo.getName(), new Tool(toolInfo, proxy));
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch tools from proxy", e);
            }
        }

        // Start the listener thread
        listener.start();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();

        // Shutdown all proxies
        for (McpServerProxy proxy : proxies) {
            shutdownFutures.add(proxy.shutdown());
        }

        // Wait for all to complete
        if (shutdownFutures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.allOf(shutdownFutures.toArray(new CompletableFuture[0]));
        }
    }

    private record Tool(ToolInfo toolInfo, Operation operation, McpServerProxy proxy) {

        Tool(ToolInfo toolInfo, Operation operation) {
            this(toolInfo, operation, null);
        }

        Tool(ToolInfo toolInfo, McpServerProxy proxy) {
            this(toolInfo, null, proxy);
        }
    }

    public static McpServerBuilder builder() {
        return new McpServerBuilder();
    }
}
