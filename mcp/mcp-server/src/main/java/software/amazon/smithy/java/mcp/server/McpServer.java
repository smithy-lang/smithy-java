/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.mcp.server.PromptLoader.normalize;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.framework.model.ValidationException;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.CallToolResult;
import software.amazon.smithy.java.mcp.model.Capabilities;
import software.amazon.smithy.java.mcp.model.InitializeResult;
import software.amazon.smithy.java.mcp.model.JsonArraySchema;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveType;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.Prompts;
import software.amazon.smithy.java.mcp.model.ServerInfo;
import software.amazon.smithy.java.mcp.model.TextContent;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.mcp.model.Tools;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
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
    private final Map<String, Prompt> prompts;
    private final PromptProcessor promptProcessor;
    private final Thread listener;
    private final InputStream is;
    private final OutputStream os;
    private final String name;
    private final Map<String, McpServerProxy> proxies;
    private final Map<String, Service> services;
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicReference<JsonRpcRequest> initializeRequest = new AtomicReference<>();
    private final ToolFilter toolFilter;

    McpServer(McpServerBuilder builder) {
        this.services = builder.services;
        this.tools = createTools(builder.services);
        this.prompts = PromptLoader.loadPrompts(builder.services.values());
        this.promptProcessor = new PromptProcessor();
        this.is = builder.is;
        this.os = builder.os;
        this.name = builder.name;
        this.proxies = builder.proxyList.stream().collect(Collectors.toMap(McpServerProxy::name, Function.identity()));
        this.toolFilter = builder.toolFilter;
        this.listener = new Thread(() -> {
            try {
                this.listen();
            } catch (Exception e) {
                LOG.error("Error handling request", e);
            } finally {
                done.countDown();
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
                handleRequest(jsonRequest);
            } catch (Exception e) {
                LOG.error("Error decoding request", e);
            }
        }
    }

    private void handleRequest(JsonRpcRequest req) {
        try {
            validate(req);
            switch (req.getMethod()) {
                case "initialize" -> {
                    this.initializeRequest.set(req);
                    proxies.values().forEach(this::initialize);
                    writeResponse(req.getId(),
                            InitializeResult.builder()
                                    .capabilities(Capabilities.builder()
                                            .tools(Tools.builder().listChanged(true).build())
                                            .prompts(Prompts.builder().listChanged(true).build())
                                            .build())
                                    .serverInfo(ServerInfo.builder()
                                            .name(name)
                                            .version("1.0.0")
                                            .build())
                                    .build());
                }
                case "prompts/list" -> writeResponse(req.getId(),
                        ListPromptsResult.builder()
                                .prompts(prompts.values().stream().map(Prompt::promptInfo).toList())
                                .build());
                case "prompts/get" -> {
                    var promptName = req.getParams().getMember("name").asString();
                    var promptArguments = req.getParams().getMember("arguments");

                    var prompt = prompts.get(normalize(promptName));

                    if (prompt == null) {
                        internalError(req, new RuntimeException("Prompt not found: " + promptName));
                        return;
                    }

                    var result = promptProcessor.buildPromptResult(prompt, promptArguments);
                    writeResponse(req.getId(), result);
                }
                case "tools/list" -> writeResponse(req.getId(),
                        ListToolsResult.builder()
                                .tools(tools.values()
                                        .stream()
                                        .filter(t -> toolFilter.allowTool(t.serverId(), t.toolInfo().getName()))
                                        .map(Tool::toolInfo)
                                        .toList())
                                .build());
                case "tools/call" -> {
                    var operationName = req.getParams().getMember("name").asString();
                    var tool = tools.get(operationName);

                    // Check if this tool should be dispatched to a proxy
                    if (tool.proxy() != null) {
                        // Forward the request to the proxy
                        JsonRpcRequest proxyRequest = JsonRpcRequest.builder()
                                .id(req.getId())
                                .method(req.getMethod())
                                .params(req.getParams())
                                .jsonrpc(req.getJsonrpc())
                                .build();

                        // Get response asynchronously
                        tool.proxy().rpc(proxyRequest).thenAccept(response -> {
                            // Pass through the response directly
                            synchronized (this) {
                                try {
                                    String serializedResponse = CODEC.serializeToString(response);
                                    os.write(serializedResponse.getBytes(StandardCharsets.UTF_8));
                                    os.write('\n');
                                    os.flush();
                                } catch (Exception e) {
                                    LOG.error("Error writing proxy response", e);
                                }
                            }
                        }).exceptionally(ex -> {
                            LOG.error("Error from proxy RPC", ex);
                            internalError(req, new RuntimeException("Proxy error: " + ex.getMessage(), ex));
                            return null;
                        });

                        // Don't send a response here as it will be sent when the future completes
                        return;
                    } else {
                        // Handle locally
                        var operation = tool.operation();
                        var argumentsDoc = req.getParams().getMember("arguments");
                        var adaptedDoc = adaptDocument(argumentsDoc, operation.getApiOperation().inputSchema());
                        var input = adaptedDoc.asShape(operation.getApiOperation().inputBuilder());
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

    private void validate(JsonRpcRequest req) {
        Document id = req.getId();
        boolean isRequest = !req.getMethod().startsWith("notifications/");
        if (isRequest) {
            if (id == null) {
                throw ValidationException.builder()
                        .withoutStackTrace()
                        .message("Requests are expected to have ids")
                        .build();
            } else if (!(id.isType(ShapeType.INTEGER) || id.isType(ShapeType.STRING))) {
                throw ValidationException.builder()
                        .withoutStackTrace()
                        .message("Request id is of invalid type " + id.type().name())
                        .build();
            }
        }
    }

    private static final byte[] TOOLS_CHANGED = """
            {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}
            """.getBytes(StandardCharsets.UTF_8); // newline is important here

    public void refreshTools() {
        try {
            synchronized (os) {
                os.write(TOOLS_CHANGED);
                os.flush();
            }
        } catch (Exception e) {
            LOG.error("Failed to flush tools changed notification");
        }
    }

    public void addNewService(String id, Service service) {
        services.put(id, service);
        tools.putAll(createTools(Map.of(id, service)));
        refreshTools();
    }

    public void addNewProxy(McpServerProxy mcpServerProxy) {
        proxies.put(mcpServerProxy.name(), mcpServerProxy);
        initialize(mcpServerProxy);
        refreshTools();
    }

    public boolean containsMcpServer(String id) {
        return services.containsKey(id) || proxies.containsKey(id);
    }

    private void writeResponse(Document id, SerializableStruct value) {
        writeResponse(JsonRpcResponse.builder()
                .id(id)
                .result(Document.of(value))
                .jsonrpc("2.0")
                .build());
    }

    private void writeResponse(JsonRpcResponse response) {
        synchronized (os) {
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
        synchronized (os) {
            try {
                os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
            }
        }
    }

    private Map<String, Tool> createTools(Map<String, Service> services) {
        var tools = new ConcurrentHashMap<String, Tool>();
        for (var entry : services.entrySet()) {
            var id = entry.getKey();
            var service = entry.getValue();
            var serviceName = service.schema().id().getName();
            for (var operation : service.getAllOperations()) {
                var operationName = operation.name();
                Schema schema = operation.getApiOperation().schema();
                var toolInfo = ToolInfo.builder()
                        .name(operationName)
                        .description(createDescription(serviceName,
                                operationName,
                                schema))
                        .inputSchema(createJsonObjectSchema(operation.getApiOperation().inputSchema(), new HashSet<>()))
                        .outputSchema(
                                createJsonObjectSchema(operation.getApiOperation().outputSchema(), new HashSet<>()))
                        .build();
                tools.put(operationName, new Tool(toolInfo, id, operation));
            }
        }
        return tools;
    }

    private static JsonObjectSchema createJsonObjectSchema(Schema schema, Set<ShapeId> visited) {
        var targetId = schema.id();
        if (!visited.add(targetId)) {
            // if we're in a recursive cycle, just say "type": "object" and bail
            return JsonObjectSchema.builder().build();
        }

        var properties = new HashMap<String, Document>();
        var requiredProperties = new ArrayList<String>();
        boolean isMember = schema.isMember();
        var members = isMember ? schema.memberTarget().members() : schema.members();
        var type = isMember ? schema.memberTarget().type() : schema.type();
        for (var member : members) {
            var name = member.memberName();
            if (member.hasTrait(TraitKey.REQUIRED_TRAIT)) {
                requiredProperties.add(name);
            }

            var jsonSchema = switch (member.type()) {
                case LIST, SET -> createJsonArraySchema(member.memberTarget(), visited);
                case MAP, STRUCTURE, UNION, DOCUMENT -> createJsonObjectSchema(member.memberTarget(), visited);
                default -> createJsonPrimitiveSchema(member);
            };

            properties.put(name, Document.of(jsonSchema));
        }

        visited.remove(targetId);
        var builder = JsonObjectSchema.builder()
                .properties(properties)
                .required(requiredProperties)
                .description(memberDescription(schema));
        if (type.isShapeType(ShapeType.DOCUMENT)) {
            builder.additionalProperties(true);
        }
        return builder.build();
    }

    private static JsonArraySchema createJsonArraySchema(Schema schema, Set<ShapeId> visited) {
        var listMember = schema.listMember();
        var items = switch (listMember.type()) {
            case LIST, SET -> createJsonArraySchema(listMember.memberTarget(), visited);
            case MAP, STRUCTURE, UNION, DOCUMENT -> createJsonObjectSchema(listMember.memberTarget(), visited);
            default -> createJsonPrimitiveSchema(listMember);
        };
        return JsonArraySchema.builder()
                .description(memberDescription(schema))
                .items(Document.of(items))
                .build();
    }

    private static JsonPrimitiveSchema createJsonPrimitiveSchema(Schema member) {
        var type = switch (member.type()) {
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE -> JsonPrimitiveType.NUMBER;
            case ENUM, BLOB, STRING, BIG_DECIMAL, BIG_INTEGER -> JsonPrimitiveType.STRING;
            case TIMESTAMP -> resolveTimestampType(member.memberTarget());
            case BOOLEAN -> JsonPrimitiveType.BOOLEAN;
            default -> throw new RuntimeException(member + " is not a primitive type");
        };

        return JsonPrimitiveSchema.builder()
                .typeMember(type)
                .description(memberDescription(member))
                .build();
    }

    private static String memberDescription(Schema schema) {
        String description = null;
        var trait = schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        if (trait != null) {
            description = trait.getValue();
        }
        if (schema.isMember()) {
            var memberDescription = memberDescription(schema.memberTarget());
            if (description != null && memberDescription != null) {
                description = appendSentences(description, memberDescription);
            } else if (memberDescription != null) {
                description = memberDescription;
            }
        }
        return description;
    }

    private static JsonPrimitiveType resolveTimestampType(Schema schema) {
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (trait == null) {
            // default is epoch-seconds
            return JsonPrimitiveType.NUMBER;
        }
        return switch (trait.getFormat()) {
            case EPOCH_SECONDS -> JsonPrimitiveType.NUMBER;
            case DATE_TIME, HTTP_DATE -> JsonPrimitiveType.STRING;
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
            return documentationTrait.getValue();
        } else {
            return "This tool invokes %s API of %s.".formatted(operationName, serviceName);
        }
    }

    @Override
    public void start() {
        // Start all proxies first
        for (McpServerProxy proxy : proxies.values()) {
            initialize(proxy);
        }

        // Start the listener thread
        listener.start();
    }

    private void initialize(McpServerProxy proxy) {
        proxy.start();
        proxy.initialize(this::writeResponse, initializeRequest.get());

        // Get the tools from this proxy
        try {
            List<ToolInfo> proxyTools = proxy.listTools();

            // Add each tool to our tool map
            for (var toolInfo : proxyTools) {
                tools.put(toolInfo.getName(), new Tool(toolInfo, proxy.name(), proxy));
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch tools from proxy", e);
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();

        // Shutdown all proxies
        for (McpServerProxy proxy : proxies.values()) {
            shutdownFutures.add(proxy.shutdown());
        }

        // Wait for all to complete
        if (shutdownFutures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.allOf(shutdownFutures.toArray(new CompletableFuture[0]));
        }
    }

    public void awaitCompletion() throws InterruptedException {
        done.await();
    }

    private record Tool(
            ToolInfo toolInfo,
            String serverId,
            Operation operation,
            McpServerProxy proxy,
            boolean requiredAdapting) {

        Tool(ToolInfo toolInfo, String serverId, Operation operation) {
            this(toolInfo, serverId, operation, null, false);
        }

        Tool(ToolInfo toolInfo, String serverId, McpServerProxy proxy) {
            this(toolInfo, serverId, null, proxy, false);
        }
    }

    private static String appendSentences(String first, String second) {
        first = first.trim();
        if (!first.endsWith(".")) {
            first = first + ". ";
        }
        return first + second;
    }

    private static Document adaptDocument(Document doc, Schema schema) {
        var fromType = doc.type();
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> switch (fromType) {
                case STRING -> Document.of(new BigDecimal(doc.asString()));
                case BIG_INTEGER -> doc;
                default -> badType(fromType, toType);
            };
            case BIG_INTEGER ->
                switch (fromType) {
                    case STRING -> Document.of(new BigInteger(doc.asString()));
                    case BIG_INTEGER -> doc;
                    default -> badType(fromType, toType);
                };
            case BLOB -> switch (fromType) {
                case STRING -> Document.of(doc.asString().getBytes(StandardCharsets.UTF_8));
                case BLOB -> doc;
                default -> badType(fromType, toType);
            };
            case STRUCTURE, UNION -> {
                var convertedMembers = new HashMap<String, Document>();
                var members = schema.members();
                for (var member : members) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptDocument(memberDoc, member.memberTarget()));
                    }
                }
                yield Document.of(convertedMembers);
            }
            case LIST, SET -> {
                var listMember = schema.listMember();
                var convertedList = new ArrayList<Document>();
                for (var item : doc.asList()) {
                    convertedList.add(adaptDocument(item, listMember.memberTarget()));
                }
                yield Document.of(convertedList);
            }
            case MAP -> {
                var mapValue = schema.mapValueMember();
                var convertedMap = new HashMap<String, Document>();
                for (var entry : doc.asStringMap().entrySet()) {
                    convertedMap.put(entry.getKey(), adaptDocument(entry.getValue(), mapValue.memberTarget()));
                }
                yield Document.of(convertedMap);
            }
            default -> doc;
        };
    }

    private static Document badType(ShapeType from, ShapeType to) {
        throw new RuntimeException("Cannot convert from " + from + " to " + to);
    }

    public static McpServerBuilder builder() {
        return new McpServerBuilder();
    }
}
