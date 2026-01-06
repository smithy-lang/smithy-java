/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.DATE_TIME;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.EPOCH_SECONDS;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.HTTP_DATE;
import static software.amazon.smithy.java.mcp.server.PromptLoader.normalize;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.framework.model.ValidationException;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.java.mcp.model.CallToolResult;
import software.amazon.smithy.java.mcp.model.Capabilities;
import software.amazon.smithy.java.mcp.model.InitializeResult;
import software.amazon.smithy.java.mcp.model.JsonArraySchema;
import software.amazon.smithy.java.mcp.model.JsonDocumentSchema;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonOneOfSchema;
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
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Core MCP service that handles JSON-RPC requests and returns responses.
 * This class is responsible for processing MCP protocol logic independently
 * of transport concerns.
 */
@SmithyUnstableApi
public final class McpService {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpService.class);

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private static final TraitKey<OneOfTrait> ONE_OF_TRAIT = TraitKey.get(OneOfTrait.class);

    private final Map<String, Tool> tools;
    private final Map<String, Prompt> prompts;
    private final PromptProcessor promptProcessor;
    private final String serviceName;
    private final String version;
    private final Map<String, McpServerProxy> proxies;
    private final Map<String, Service> services;
    private final AtomicReference<JsonRpcRequest> initializeRequest = new AtomicReference<>();
    private final ToolFilter toolFilter;
    private final AtomicReference<Boolean> proxiesInitialized = new AtomicReference<>(false);
    private final McpMetricsObserver metricsObserver;
    private final SchemaIndex schemaIndex;

    McpService(
            Map<String, Service> services,
            List<McpServerProxy> proxyList,
            String name,
            String version,
            ToolFilter toolFilter,
            McpMetricsObserver metricsObserver
    ) {
        this.services = services;
        this.schemaIndex =
                SchemaIndex.compose(services.values().stream().map(Service::schemaIndex).toArray(SchemaIndex[]::new));
        this.tools = createTools(services);
        this.prompts = PromptLoader.loadPrompts(services.values());
        this.promptProcessor = new PromptProcessor();
        this.serviceName = name;
        this.version = version;
        this.proxies = proxyList.stream().collect(Collectors.toMap(McpServerProxy::name, p -> p));
        this.toolFilter = toolFilter;
        this.metricsObserver = metricsObserver;
    }

    /**
     * Handles a JSON-RPC request synchronously and returns a response.
     * For proxy tool calls, the response callback is invoked asynchronously and this method returns null.
     * For local operations, the response is returned immediately.
     *
     * @param req The JSON-RPC request to handle
     * @param asyncResponseCallback Callback for async responses (used for proxy calls)
     * @param protocolVersion The protocol version for this request (may be null)
     * @return The response for synchronous operations, or null for async operations
     */
    public JsonRpcResponse handleRequest(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion
    ) {
        try {
            validate(req);
            var method = req.getMethod();
            return switch (method) {
                case "initialize" -> handleInitialize(req);
                default -> {
                    initializeProxies(rpcResponse -> {});
                    yield switch (method) {
                        case "prompts/list" -> handlePromptsList(req);
                        case "prompts/get" -> handlePromptsGet(req);
                        case "tools/list" -> handleToolsList(req, protocolVersion);
                        case "tools/call" -> handleToolsCall(req, asyncResponseCallback, protocolVersion);
                        default -> null; // Notifications or unknown methods
                    };
                }
            };
        } catch (Exception e) {
            return createErrorResponse(req, e);
        }
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest req) {
        if (metricsObserver != null) {
            var params = req.getParams();
            var clientInfo = params.getMember("clientInfo");
            var capabilities = params.getMember("capabilities");

            String extractedProtocolVersion = params.getMember("protocolVersion") != null
                    ? params.getMember("protocolVersion").asString()
                    : null;

            String clientName = clientInfo != null && clientInfo.getMember("name") != null
                    ? clientInfo.getMember("name").asString()
                    : null;

            String clientTitle = clientInfo != null && clientInfo.getMember("title") != null
                    ? clientInfo.getMember("title").asString()
                    : null;

            boolean rootsListChanged = capabilities != null
                    && capabilities.getMember("roots") != null
                    && capabilities.getMember("roots").getMember("listChanged") != null
                    && capabilities.getMember("roots").getMember("listChanged").asBoolean();

            boolean sampling = capabilities != null && capabilities.getMember("sampling") != null;
            boolean elicitation = capabilities != null && capabilities.getMember("elicitation") != null;

            metricsObserver.onInitialize("initialize",
                    extractedProtocolVersion,
                    rootsListChanged,
                    sampling,
                    elicitation,
                    clientName,
                    clientTitle);
        }

        this.initializeRequest.compareAndSet(null, req);

        initializeProxies(rpcResponse -> {});

        var maybeVersion = req.getParams().getMember("protocolVersion");
        String pv = null;
        if (maybeVersion != null) {
            var protocolVersion = ProtocolVersion.version(maybeVersion.asString());
            if (!(protocolVersion instanceof ProtocolVersion.UnknownVersion)) {
                pv = protocolVersion.identifier();
            }
        }

        var builder = InitializeResult.builder();
        if (pv != null) {
            builder.protocolVersion(pv);
        }

        var result = builder
                .capabilities(Capabilities.builder()
                        .tools(Tools.builder().listChanged(true).build())
                        .prompts(Prompts.builder().listChanged(true).build())
                        .build())
                .serverInfo(ServerInfo.builder()
                        .name(serviceName)
                        .version(version)
                        .build())
                .build();

        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handlePromptsList(JsonRpcRequest req) {
        var result = ListPromptsResult.builder()
                .prompts(prompts.values().stream().map(Prompt::promptInfo).toList())
                .build();
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handlePromptsGet(JsonRpcRequest req) {
        var promptName = req.getParams().getMember("name").asString();
        var promptArguments = req.getParams().getMember("arguments");

        var prompt = prompts.get(normalize(promptName));

        if (prompt == null) {
            throw new RuntimeException("Prompt not found: " + promptName);
        }

        var result = promptProcessor.buildPromptResult(prompt, promptArguments);
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handleToolsList(JsonRpcRequest req, ProtocolVersion protocolVersion) {
        var supportsOutputSchema = supportsOutputSchema(protocolVersion);
        var result = ListToolsResult.builder()
                .tools(tools.values()
                        .stream()
                        .filter(t -> toolFilter.allowTool(t.serverId(), t.toolInfo().getName()))
                        .map(tool -> extractToolInfo(tool, supportsOutputSchema))
                        .toList())
                .build();
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handleToolsCall(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion
    ) {
        if (metricsObserver != null) {
            String toolName = req.getParams().getMember("name") != null
                    ? req.getParams().getMember("name").asString()
                    : null;
            metricsObserver.onToolCall("tools/call", toolName);
        }

        var operationName = req.getParams().getMember("name").asString();
        var tool = tools.get(operationName);

        if (tool == null) {
            return createErrorResponse(req, "No such tool: " + operationName);
        }

        // Check if this tool should be dispatched to a proxy
        if (tool.proxy() != null) {
            // Forward the request to the proxy
            JsonRpcRequest proxyRequest = JsonRpcRequest.builder()
                    .id(req.getId())
                    .method(req.getMethod())
                    .params(req.getParams())
                    .jsonrpc(req.getJsonrpc())
                    .build();

            // Get response asynchronously and invoke callback
            tool.proxy().rpc(proxyRequest).thenAccept(asyncResponseCallback).exceptionally(ex -> {
                LOG.error("Error from proxy RPC", ex);
                asyncResponseCallback
                        .accept(createErrorResponse(req, new RuntimeException("Proxy error: " + ex.getMessage(), ex)));
                return null;
            });

            // Return null to indicate async handling
            return null;
        } else {
            // Handle locally
            var operation = tool.operation();
            var argumentsDoc = req.getParams().getMember("arguments");
            var adaptedDoc = adaptDocument(argumentsDoc, operation.getApiOperation().inputSchema());
            var input = adaptedDoc.asShape(operation.getApiOperation().inputBuilder());
            var output = operation.function().apply(input, null);
            var result = formatStructuredContent(tool, (SerializableShape) output, protocolVersion);
            return createSuccessResponse(req.getId(), result);
        }
    }

    /**
     * Starts proxies without initializing them.
     */
    public void startProxies() {
        for (McpServerProxy proxy : proxies.values()) {
            try {
                proxy.start();
            } catch (Exception e) {
                LOG.error("Failed to start proxy: " + proxy.name(), e);
            }
        }
    }

    /**
     * Initializes proxies with the actual initialize request.
     */
    public void initializeProxies(Consumer<JsonRpcResponse> responseWriter) {
        if (proxiesInitialized.compareAndSet(false, true)) {
            JsonRpcRequest initRequest = initializeRequest.get();
            var protocolVersion = ProtocolVersion.defaultVersion();
            if (initRequest != null) {
                var maybeVersion = initRequest.getParams().getMember("protocolVersion");
                if (maybeVersion != null) {
                    var pv = ProtocolVersion.version(maybeVersion.asString());
                    if (!(pv instanceof ProtocolVersion.UnknownVersion)) {
                        protocolVersion = pv;
                    }
                }
            }

            for (McpServerProxy proxy : proxies.values()) {
                if (initRequest != null) {
                    proxy.initialize(responseWriter, initRequest, protocolVersion);
                }

                List<ToolInfo> proxyTools = proxy.listTools();
                for (var toolInfo : proxyTools) {
                    tools.put(toolInfo.getName(), new Tool(toolInfo, proxy.name(), proxy));
                }
            }
        }
    }

    /**
     * Gets the current initialize request if one has been received.
     */
    public JsonRpcRequest getInitializeRequest() {
        return initializeRequest.get();
    }

    /**
     * Adds a new service and updates the tools map.
     */
    public void addNewService(String id, Service service) {
        services.put(id, service);
        tools.putAll(createTools(Map.of(id, service)));
    }

    /**
     * Adds a new proxy and initializes it.
     */
    public void addNewProxy(McpServerProxy mcpServerProxy, Consumer<JsonRpcResponse> responseWriter) {
        proxies.put(mcpServerProxy.name(), mcpServerProxy);
        mcpServerProxy.start();

        try {
            List<ToolInfo> proxyTools = mcpServerProxy.listTools();
            for (var toolInfo : proxyTools) {
                tools.put(toolInfo.getName(), new Tool(toolInfo, mcpServerProxy.name(), mcpServerProxy));
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch tools from proxy", e);
        }
    }

    /**
     * Checks if a service or proxy with the given ID exists.
     */
    public boolean containsMcpServer(String id) {
        return services.containsKey(id) || proxies.containsKey(id);
    }

    /**
     * Returns all registered proxies.
     */
    public Map<String, McpServerProxy> getProxies() {
        return proxies;
    }

    private boolean supportsOutputSchema(ProtocolVersion protocolVersion) {
        return protocolVersion != null && protocolVersion.compareTo(ProtocolVersion.v2025_06_18.INSTANCE) >= 0;
    }

    private CallToolResult formatStructuredContent(
            Tool tool,
            SerializableShape output,
            ProtocolVersion protocolVersion
    ) {
        var adaptedOutput = adaptOutputDocument(Document.of(output), tool.operation().getApiOperation().outputSchema());
        var result = CallToolResult.builder()
                .content(List.of(TextContent.builder()
                        .text(CODEC.serializeToString(adaptedOutput))
                        .build()));

        if (supportsOutputSchema(protocolVersion)) {
            result.structuredContent(adaptedOutput);
        }

        return result.build();
    }

    private ToolInfo extractToolInfo(Tool tool, boolean supportsOutput) {
        var toolInfo = tool.toolInfo();
        if (supportsOutput || toolInfo.getOutputSchema() == null) {
            return toolInfo;
        }
        return toolInfo.toBuilder()
                .outputSchema(null)
                .build();
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

    private JsonRpcResponse createSuccessResponse(Document id, SerializableShape value) {
        return JsonRpcResponse.builder()
                .id(id)
                .result(Document.of(value))
                .jsonrpc("2.0")
                .build();
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, Exception exception) {
        return createErrorResponse(req, exception, true); //TODO change the default to false.
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, Throwable exception, boolean sendStackTrace) {
        String s;
        exception = unwrapException(exception);
        if (sendStackTrace) {
            try (var sw = new StringWriter();
                    var pw = new PrintWriter(sw)) {
                exception.printStackTrace(pw);
                s = sw.toString().replace("\n", "| ");
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
                throw new RuntimeException(e);
            }
        } else {
            s = exception.getMessage();
        }
        return createErrorResponse(req, s);
    }

    private Throwable unwrapException(Throwable exception) {
        return switch (exception) {
            case CompletionException ce when ce.getCause() != null -> ce.getCause();
            case ExecutionException ee when ee.getCause() != null -> ee.getCause();
            default -> exception;
        };
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, String s) {
        var error = JsonRpcErrorResponse.builder()
                .code(500)
                .message(s)
                .build();
        return JsonRpcResponse.builder()
                .id(req.getId())
                .error(error)
                .jsonrpc("2.0")
                .build();
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

    private JsonObjectSchema createJsonObjectSchema(Schema schema, Set<ShapeId> visited) {
        var targetId = schema.id();
        if (!visited.add(targetId)) {
            // if we're in a recursive cycle, just say "type": "object" and bail
            return JsonObjectSchema.builder().build();
        }

        var properties = new HashMap<String, Document>();
        var requiredProperties = new ArrayList<String>();
        boolean isMember = schema.isMember();
        var members = isMember ? schema.memberTarget().members() : schema.members();
        for (var member : members) {
            var name = member.memberName();
            if (member.hasTrait(TraitKey.REQUIRED_TRAIT)) {
                requiredProperties.add(name);
            }

            var jsonSchema = createMemberSchema(member, visited);

            properties.put(name, Document.of(jsonSchema));
        }

        visited.remove(targetId);
        return JsonObjectSchema.builder()
                .properties(properties)
                .required(requiredProperties)
                .description(memberDescription(schema))
                .build();
    }

    private JsonArraySchema createJsonArraySchema(Schema schema, Set<ShapeId> visited) {
        var listMember = schema.listMember();
        var items = createMemberSchema(listMember, visited);

        // For sparse lists, allow null items using anyOf
        Document itemsSchema;
        if (schema.hasTrait(TraitKey.SPARSE_TRAIT)) {
            var nullSchema = Map.of("type", Document.of("null"));
            itemsSchema = Document.of(Map.of(
                    "anyOf",
                    Document.of(List.of(Document.of(items), Document.of(nullSchema)))));
        } else {
            itemsSchema = Document.of(items);
        }

        return JsonArraySchema.builder()
                .description(memberDescription(schema))
                .items(itemsSchema)
                .build();
    }

    private JsonPrimitiveSchema createJsonPrimitiveSchema(Schema member) {
        var type = switch (member.type()) {
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE -> JsonPrimitiveType.NUMBER;
            case ENUM, BLOB, STRING, BIG_DECIMAL, BIG_INTEGER, TIMESTAMP -> JsonPrimitiveType.STRING;
            case BOOLEAN -> JsonPrimitiveType.BOOLEAN;
            default -> throw new RuntimeException(member + " is not a primitive type");
        };

        var builder = JsonPrimitiveSchema.builder()
                .type(type)
                .description(memberDescription(member));

        // Add format annotation for timestamps per JSON Schema spec
        if (member.type() == ShapeType.TIMESTAMP) {
            builder.format("date-time");
        }

        List<Document> enumValues = switch (member.type()) {
            case ENUM, STRING -> member.stringEnumValues().stream().map(Document::of).toList();
            case INT_ENUM -> member.intEnumValues().stream().map(Document::of).toList();
            default -> List.of();
        };

        if (!enumValues.isEmpty()) {
            builder.enumValues(enumValues);
        }

        return builder.build();
    }

    private static final List<String> DOCUMENT_TYPES = List.of(
            "string",
            "number",
            "boolean",
            "object",
            "array",
            "null");

    private JsonDocumentSchema createJsonDocumentSchema(Schema member) {
        return JsonDocumentSchema.builder()
                .type(DOCUMENT_TYPES)
                .description(memberDescription(member))
                .build();
    }

    private SerializableShape createJsonDocumentSchema(Schema member, Set<ShapeId> visited) {
        var targetSchema = member.isMember() ? member.memberTarget() : member;
        var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

        if (oneOfTrait != null) {
            return createJsonOneOfSchema(oneOfTrait, member, visited);
        } else {
            return createJsonDocumentSchema(member);
        }
    }

    private JsonOneOfSchema createJsonOneOfSchema(
            OneOfTrait oneOfTrait,
            Schema documentMember,
            Set<ShapeId> visited
    ) {
        var oneOfVariants = new ArrayList<Document>();

        for (var memberDef : oneOfTrait.getMembers()) {
            var memberName = memberDef.getName();
            var targetShapeId = memberDef.getTarget();

            var targetSchema = schemaIndex.getSchema(targetShapeId);
            var memberSchema = createJsonObjectSchema(targetSchema, visited);

            oneOfVariants.add(createUnionVariant(memberName, memberSchema));
        }

        return JsonOneOfSchema.builder()
                .oneOf(oneOfVariants)
                .description(memberDescription(documentMember))
                .build();
    }

    private SerializableShape createJsonUnionSchema(Schema schema, Set<ShapeId> visited) {
        var targetId = schema.id();
        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var variants = new ArrayList<Document>();

        for (var member : schema.members()) {
            var memberName = member.memberName();
            var memberSchema = createMemberSchema(member, visited);

            variants.add(createUnionVariant(memberName, memberSchema));
        }

        visited.remove(targetId);
        return JsonOneOfSchema.builder()
                .oneOf(variants)
                .description(memberDescription(schema))
                .build();
    }

    private static Document createUnionVariant(String memberName, SerializableShape memberSchema) {
        var wrapperSchema = JsonObjectSchema.builder()
                .properties(Map.of(memberName, Document.of(memberSchema)))
                .required(List.of(memberName))
                .additionalProperties(Document.of(false))
                .build();
        return Document.of(wrapperSchema);
    }

    private SerializableShape createMemberSchema(Schema member, Set<ShapeId> visited) {
        return switch (member.type()) {
            case LIST, SET -> createJsonArraySchema(member.memberTarget(), visited);
            case MAP -> createJsonMapSchema(member.memberTarget(), visited);
            case STRUCTURE -> createJsonObjectSchema(member.memberTarget(), visited);
            case UNION -> createJsonUnionSchema(member.memberTarget(), visited);
            case DOCUMENT -> createJsonDocumentSchema(member, visited);
            default -> createJsonPrimitiveSchema(member);
        };
    }

    private JsonObjectSchema createJsonMapSchema(Schema schema, Set<ShapeId> visited) {
        var mapValueMember = schema.mapValueMember();
        var valueSchema = createMemberSchema(mapValueMember, visited);

        // For sparse maps, allow null values using anyOf
        Document additionalPropertiesSchema;
        if (schema.hasTrait(TraitKey.SPARSE_TRAIT)) {
            var nullSchema = Map.of("type", Document.of("null"));
            additionalPropertiesSchema = Document.of(Map.of(
                    "anyOf",
                    Document.of(List.of(Document.of(valueSchema), Document.of(nullSchema)))));
        } else {
            additionalPropertiesSchema = Document.of(valueSchema);
        }

        return JsonObjectSchema.builder()
                .description(memberDescription(schema))
                .additionalProperties(additionalPropertiesSchema)
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

    private Document adaptDocument(Document doc, Schema schema) {
        if (doc == null) {
            return null;
        }
        var fromType = doc.type();
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> switch (fromType) {
                case STRING -> Document.of(new BigDecimal(doc.asString()));
                case BIG_INTEGER -> doc;
                default -> badType(fromType, toType);
            };
            case BIG_INTEGER -> switch (fromType) {
                case STRING -> Document.of(new BigInteger(doc.asString()));
                case BIG_INTEGER -> doc;
                default -> badType(fromType, toType);
            };
            case BLOB -> switch (fromType) {
                case STRING -> Document.of(Base64.getDecoder().decode(doc.asString()));
                case BLOB -> doc;
                default -> badType(fromType, toType);
            };
            case TIMESTAMP -> adaptTimestamp(doc);
            case STRUCTURE -> {
                var convertedMembers = new HashMap<String, Document>();
                var members = schema.members();
                for (var member : members) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptDocument(memberDoc, member));
                    }
                }
                yield Document.of(convertedMembers);
            }
            case UNION -> {
                var convertedMembers = new HashMap<String, Document>();

                // Find which member is set and adapt it
                // Input is in wrapper format: {"circle": {...}}
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptDocument(memberDoc, member));
                        break;
                    }
                }
                yield Document.of(convertedMembers);
            }
            case LIST, SET -> {
                var listMember = schema.listMember();
                var convertedList = new ArrayList<Document>();
                for (var item : doc.asList()) {
                    convertedList.add(adaptDocument(item, listMember));
                }
                yield Document.of(convertedList);
            }
            case MAP -> {
                var mapValue = schema.mapValueMember();
                var convertedMap = new HashMap<String, Document>();
                for (var entry : doc.asStringMap().entrySet()) {
                    convertedMap.put(entry.getKey(), adaptDocument(entry.getValue(), mapValue));
                }
                yield Document.of(convertedMap);
            }
            case DOCUMENT -> adaptDocumentWithOneOf(doc, schema);
            default -> doc;
        };
    }

    private Document adaptDocumentWithOneOf(Document doc, Schema schema) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

        if (oneOfTrait != null) {
            // MCP sends wrapper format: {"circle": {"radius": 5}}
            // Need to convert to discriminated format: {"__type": "smithy.example#Circle", "radius": 5}
            var discriminator = oneOfTrait.getDiscriminator();

            // Find which member is set in the wrapper
            for (var memberDef : oneOfTrait.getMembers()) {
                var memberName = memberDef.getName();
                var memberDoc = doc.getMember(memberName);
                if (memberDoc != null) {
                    // Build the flat object with discriminator
                    var flatMembers = new HashMap<String, Document>();
                    var memberId = memberDef.getTarget();
                    flatMembers.put(discriminator, Document.of(memberId.toString()));
                    // Copy all fields from the inner object
                    var memberSchema = schemaIndex.getSchema(memberId);
                    flatMembers.putAll(adaptDocument(memberDoc, memberSchema).asStringMap());
                    return Document.of(flatMembers);
                }
            }
            // Fallback - return as-is if can't determine type
        }
        return doc;
    }

    private static Document badType(ShapeType from, ShapeType to) {
        throw new RuntimeException("Cannot convert from " + from + " to " + to);
    }

    /**
     *  This is primarily for more robustness against AI hallucinations.
     */
    private static Document adaptTimestamp(Document doc) {
        // If already a timestamp, format as date-time string
        if (doc.isType(ShapeType.TIMESTAMP)) {
            return Document.of(DATE_TIME.writeString(doc.asTimestamp()));
        }
        // If input is a string, try DATE_TIME first, fallback to HTTP_DATE
        if (doc.isType(ShapeType.STRING)) {
            var str = doc.asString();
            try {
                return Document.of(DATE_TIME.readFromString(str, false));
            } catch (Exception e) {
                // Fallback to HTTP_DATE format
                return Document.of(HTTP_DATE.readFromString(str, false));
            }
        }
        // If input is a number, use epoch seconds
        return Document.of(EPOCH_SECONDS.readFromNumber(doc.asNumber()));
    }

    private Document adaptOutputDocument(Document doc, Schema schema) {
        if (doc == null) {
            return null;
        }
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> Document.of(doc.asBigDecimal().toString());
            case BIG_INTEGER -> Document.of(doc.asBigInteger().toString());
            case BLOB -> Document.of(Base64.getEncoder().encodeToString(ByteBufferUtils.getBytes(doc.asBlob())));
            // Use adaptTimestamp() instead of asTimestamp() because oneOf union members are
            // deserialized as untyped Documents (no schema available). Timestamps in these
            // documents remain as strings or numbers rather than being converted to Timestamp Documents.
            case TIMESTAMP -> adaptTimestamp(doc);
            case STRUCTURE -> {
                var convertedMembers = new HashMap<String, Document>();
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptOutputDocument(memberDoc, member));
                    }
                }
                yield Document.of(convertedMembers);
            }
            case UNION -> {
                // Regular union - already in wrapper format: {"circle": {...}}
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        var adaptedMemberDoc = adaptOutputDocument(memberDoc, member);
                        yield Document.of(Map.of(memberName, adaptedMemberDoc));
                    }
                }
                yield Document.of(Map.of());
            }
            case LIST, SET -> {
                var listMember = schema.listMember();
                var convertedList = new ArrayList<Document>();
                for (var item : doc.asList()) {
                    convertedList.add(adaptOutputDocument(item, listMember));
                }
                yield Document.of(convertedList);
            }
            case MAP -> {
                var mapValue = schema.mapValueMember();
                var convertedMap = new HashMap<String, Document>();
                for (var entry : doc.asStringMap().entrySet()) {
                    convertedMap.put(entry.getKey(), adaptOutputDocument(entry.getValue(), mapValue));
                }
                yield Document.of(convertedMap);
            }
            case DOCUMENT -> {
                var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
                var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

                if (oneOfTrait != null) {
                    // External service returns: {"__type": "smithy.example#Circle", "radius": 5}
                    // Need to convert to MCP wrapper format: {"circle": {"radius": 5}}
                    var discriminator = oneOfTrait.getDiscriminator();
                    var discriminatorValue = doc.getMember(discriminator);

                    if (discriminatorValue != null) {
                        var shapeId = ShapeId.from(discriminatorValue.asString());
                        // Find the matching member definition
                        for (var memberDef : oneOfTrait.getMembers()) {
                            if (memberDef.getTarget().equals(shapeId)) {
                                var memberName = memberDef.getName();
                                var memberSchema = schemaIndex.getSchema(shapeId);
                                // Build the inner object without the discriminator field
                                var innerMembers = new HashMap<>(adaptOutputDocument(doc, memberSchema).asStringMap());
                                innerMembers.remove(discriminator);
                                // Return wrapper format
                                yield Document.of(Map.of(memberName, Document.of(innerMembers)));
                            }
                        }
                    }
                }
                yield doc;
            }
            default -> doc;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Service> services = new HashMap<>();
        private List<McpServerProxy> proxyList = new ArrayList<>();
        private String name = "mcp-server";
        private String version = "1.0.0";
        private ToolFilter toolFilter = (serverId, toolName) -> true;
        private McpMetricsObserver metricsObserver;

        public Builder services(Map<String, Service> services) {
            this.services = services;
            return this;
        }

        public Builder proxyList(List<McpServerProxy> proxyList) {
            this.proxyList = proxyList;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder toolFilter(ToolFilter toolFilter) {
            this.toolFilter = toolFilter;
            return this;
        }

        public Builder metricsObserver(McpMetricsObserver metricsObserver) {
            this.metricsObserver = metricsObserver;
            return this;
        }

        public McpService build() {
            return new McpService(services, proxyList, name, version, toolFilter, metricsObserver);
        }
    }
}
