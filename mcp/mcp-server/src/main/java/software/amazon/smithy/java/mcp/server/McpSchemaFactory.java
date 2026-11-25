/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.ai.McpHeaderTrait;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.java.mcp.model.JsonArraySchema;
import software.amazon.smithy.java.mcp.model.JsonDocumentSchema;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonOneOfSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveType;
import software.amazon.smithy.java.mcp.model.ToolAnnotations;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Converts Smithy operation schemas into canonical MCP tool descriptors.
 */
final class McpSchemaFactory {
    private static final InternalLogger LOG = InternalLogger.getLogger(McpSchemaFactory.class);
    private static final TraitKey<OneOfTrait> ONE_OF_TRAIT = TraitKey.get(OneOfTrait.class);
    private static final TraitKey<McpHeaderTrait> MCP_HEADER_TRAIT = TraitKey.get(McpHeaderTrait.class);
    private static final List<String> DOCUMENT_TYPES = List.of(
            "string",
            "number",
            "boolean",
            "object",
            "array",
            "null");

    private final SchemaIndex schemaIndex;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    McpSchemaFactory(SchemaIndex schemaIndex) {
        this.schemaIndex = schemaIndex;
    }

    Map<String, McpToolDescriptor> createTools(Map<String, Service> services) {
        var tools = new HashMap<String, McpToolDescriptor>();
        for (var entry : services.entrySet()) {
            var serverId = entry.getKey();
            var service = entry.getValue();
            for (var operation : service.getAllOperations()) {
                var descriptor = createTool(serverId, service, operation);
                tools.put(descriptor.info().getName(), descriptor);
            }
        }
        return tools;
    }

    McpToolDescriptor createTool(String serverId, Service service, Operation operation) {
        var operationSchema = operation.getApiOperation().schema();
        var operationName = operation.name();
        var cache = new HashMap<ShapeId, SerializableShape>();
        var info = ToolInfo.builder()
                .name(operationName)
                .description(createDescription(service.schema().id().getName(), operationName, operationSchema))
                .inputSchema(createRootSchema(operation.getApiOperation().inputSchema(), cache))
                .outputSchema(createRootSchema(operation.getApiOperation().outputSchema(), cache))
                .annotations(createAnnotations(operationSchema))
                .build();
        return new McpToolDescriptor(
                info,
                serverId,
                new McpToolDescriptor.LocalTarget(operation),
                localHeaderParameters(operation));
    }

    private Map<String, String> localHeaderParameters(Operation operation) {
        var result = new HashMap<String, String>();
        var inputSchema = operation.getApiOperation().inputSchema();
        var omitted = SmithyDocumentAdapter.overlappingMembers(inputSchema);
        for (var member : inputSchema.members()) {
            var trait = member.getTrait(MCP_HEADER_TRAIT);
            if (trait != null
                    && !omitted.contains(member.memberName())
                    && trait.getValue().matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
                result.put(McpJson.wireName(member), trait.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private ToolAnnotations createAnnotations(Schema operationSchema) {
        boolean readOnly = operationSchema.hasTrait(TraitKey.READ_ONLY_TRAIT);
        boolean idempotent = operationSchema.hasTrait(TraitKey.IDEMPOTENT_TRAIT);
        if (!readOnly && !idempotent) {
            return null;
        }
        var builder = ToolAnnotations.builder();
        if (readOnly) {
            builder.readOnlyHint(true);
        }
        if (idempotent) {
            builder.idempotentHint(true);
        }
        return builder.build();
    }

    private JsonObjectSchema createObjectSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = target.id();
        var cached = cache.get(targetId);
        if (cached != null) {
            return (JsonObjectSchema) withDescription(cached, memberDescription(member));
        }
        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var properties = new HashMap<String, Document>();
        var required = new ArrayList<String>();
        var omitted = overlappingMembers(target);
        for (var child : target.members()) {
            if (omitted.contains(child.memberName())) {
                continue;
            }
            var wireName = McpJson.wireName(child);
            if (child.hasTrait(TraitKey.REQUIRED_TRAIT)) {
                required.add(wireName);
            }
            properties.put(wireName, Document.of(createSchema(child, visited, cache)));
        }
        visited.remove(targetId);

        var result = JsonObjectSchema.builder()
                .properties(properties)
                .required(required)
                .build();
        cache.put(targetId, result);
        return (JsonObjectSchema) withDescription(result, memberDescription(member));
    }

    private JsonObjectSchema createRootSchema(Schema root, Map<ShapeId, SerializableShape> cache) {
        return asJsonObjectSchema(root, createSchema(root, new HashSet<>(), cache));
    }

    /**
     * Coerces a rendered root schema into the object-typed schema {@link ToolInfo} requires. A
     * polymorphic root ({@link JsonOneOfSchema}) becomes an object constrained by the same
     * {@code oneOf} variants; on the wire this only adds the {@code $schema} annotation, since
     * {@link JsonOneOfSchema} already declares {@code "type": "object"}. An untyped document root
     * becomes a permissive object. Anything else has no object representation and degrades to a
     * permissive object schema with a warning rather than failing the entire tool listing.
     */
    private static JsonObjectSchema asJsonObjectSchema(Schema root, SerializableShape schema) {
        return switch (schema) {
            case JsonObjectSchema object -> object;
            case JsonOneOfSchema oneOf -> {
                var builder = JsonObjectSchema.builder().oneOf(oneOf.getOneOf());
                if (oneOf.getDescription() != null) {
                    builder.description(oneOf.getDescription());
                }
                yield builder.build();
            }
            case JsonDocumentSchema document -> {
                var builder = JsonObjectSchema.builder();
                if (document.getDescription() != null) {
                    builder.description(document.getDescription());
                }
                yield builder.build();
            }
            default -> JsonObjectSchema.builder().build();
        };
    }

    private JsonArraySchema createArraySchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var items = createSchema(target.listMember(), visited, cache);
        var itemDocument = target.hasTrait(TraitKey.SPARSE_TRAIT)
                ? Document.of(Map.of(
                        "anyOf",
                        Document.of(List.of(
                                Document.of(items),
                                Document.of(Map.of("type", Document.of("null")))))))
                : Document.of(items);
        return JsonArraySchema.builder()
                .description(memberDescription(member))
                .items(itemDocument)
                .build();
    }

    private JsonPrimitiveSchema createPrimitiveSchema(Schema member) {
        var type = switch (member.type()) {
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE -> JsonPrimitiveType.NUMBER;
            case ENUM, BLOB, STRING, BIG_DECIMAL, BIG_INTEGER, TIMESTAMP -> JsonPrimitiveType.STRING;
            case BOOLEAN -> JsonPrimitiveType.BOOLEAN;
            default -> throw new IllegalArgumentException(member + " is not a primitive type");
        };

        var builder = JsonPrimitiveSchema.builder()
                .type(type)
                .description(memberDescription(member));
        var header = member.getTrait(MCP_HEADER_TRAIT);
        if (header != null) {
            builder.mcpHeader(header.getValue());
        }
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

    private SerializableShape createDocumentSchema(
            Schema member,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var target = member.isMember() ? member.memberTarget() : member;
        var oneOf = target.getTrait(ONE_OF_TRAIT);
        if (oneOf == null) {
            return JsonDocumentSchema.builder()
                    .type(DOCUMENT_TYPES)
                    .description(memberDescription(member))
                    .build();
        }
        return createOneOfSchema(oneOf, member, visited, cache);
    }

    private SerializableShape createOneOfSchema(
            OneOfTrait oneOf,
            Schema documentMember,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = (documentMember.isMember() ? documentMember.memberTarget() : documentMember).id();
        var cached = cache.get(targetId);
        if (cached != null) {
            return withDescription(cached, memberDescription(documentMember));
        }
        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var variants = new ArrayList<Document>();
        for (var definition : oneOf.getMembers()) {
            var variant = schemaIndex.getSchema(definition.getTarget());
            variants.add(createUnionVariant(
                    definition.getName(),
                    withoutOmittedMembers(
                            targetId,
                            oneOf.getDiscriminator(),
                            variant,
                            createSchema(variant, visited, cache))));
        }
        visited.remove(targetId);

        var result = JsonOneOfSchema.builder().oneOf(variants).build();
        cache.put(targetId, result);
        return withDescription(result, memberDescription(documentMember));
    }

    /**
     * The members of a structure or union that are not exposed, see
     * {@link SmithyDocumentAdapter#overlappingMembers}. They are left out with one warning per shape rather than
     * failing the entire tool listing.
     */
    private Set<String> overlappingMembers(Schema container) {
        var omitted = SmithyDocumentAdapter.overlappingMembers(container);
        if (!omitted.isEmpty() && warned.add(container.id().toString())) {
            LOG.warn(
                    "Members {} of {} are not part of any tool schema: one is named in JSON like another one is"
                            + " named in the model, or two share one JSON name, so a key with that name cannot be"
                            + " attributed",
                    new TreeSet<>(omitted),
                    container.id());
        }
        return omitted;
    }

    /**
     * Leaves the members that are named like the discriminator out of a variant, see
     * {@link SmithyDocumentAdapter#omittedMembers}, with a warning. Members with overlapping names are left out
     * of the structure itself.
     */
    private SerializableShape withoutOmittedMembers(
            ShapeId document,
            String discriminator,
            Schema variant,
            SerializableShape variantSchema
    ) {
        var named = new TreeSet<>(SmithyDocumentAdapter.omittedMembers(variant, discriminator));
        named.removeAll(SmithyDocumentAdapter.overlappingMembers(variant));
        if (named.isEmpty() || !(variantSchema instanceof JsonObjectSchema object)) {
            return variantSchema;
        }
        if (warned.add(document + " " + variant.id())) {
            LOG.warn(
                    "Members {} of {} are not part of the tool schema: within the @oneOf document {} they cannot"
                            + " be told apart from its discriminator {}",
                    named,
                    variant.id(),
                    document,
                    discriminator);
        }
        if (object.getProperties() == null) {
            return variantSchema;
        }
        var wireNames = new HashSet<String>();
        for (var name : named) {
            wireNames.add(McpJson.wireName(variant.member(name)));
        }
        var properties = new HashMap<>(object.getProperties());
        properties.keySet().removeAll(wireNames);
        var builder = object.toBuilder().properties(properties);
        if (object.getRequired() != null) {
            builder.required(object.getRequired().stream().filter(name -> !wireNames.contains(name)).toList());
        }
        return builder.build();
    }

    private SerializableShape createUnionSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = target.id();
        var cached = cache.get(targetId);
        if (cached != null) {
            return withDescription(cached, memberDescription(member));
        }
        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var variants = new ArrayList<Document>();
        var omitted = overlappingMembers(target);
        for (var child : target.members()) {
            if (omitted.contains(child.memberName())) {
                continue;
            }
            variants.add(createUnionVariant(
                    McpJson.wireName(child),
                    createSchema(child, visited, cache)));
        }
        visited.remove(targetId);
        if (variants.isEmpty()) {
            // No member is left to choose from. A list of alternatives must not be empty, so the one
            // alternative is the schema that accepts nothing: the union has no value, and can only be absent.
            variants.add(Document.of(false));
        }

        var result = JsonOneOfSchema.builder().oneOf(variants).build();
        cache.put(targetId, result);
        return withDescription(result, memberDescription(member));
    }

    /**
     * Renders any schema, member or not, by dispatching on the type of the shape it resolves to.
     */
    private SerializableShape createSchema(
            Schema schema,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var target = schema.isMember() ? schema.memberTarget() : schema;
        return switch (target.type()) {
            case LIST, SET -> createArraySchema(schema, target, visited, cache);
            case MAP -> createMapSchema(schema, target, visited, cache);
            case STRUCTURE -> createObjectSchema(schema, target, visited, cache);
            case UNION -> createUnionSchema(schema, target, visited, cache);
            case DOCUMENT -> createDocumentSchema(schema, visited, cache);
            default -> createPrimitiveSchema(schema);
        };
    }

    private JsonObjectSchema createMapSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var value = createSchema(target.mapValueMember(), visited, cache);
        var additionalProperties = target.hasTrait(TraitKey.SPARSE_TRAIT)
                ? Document.of(Map.of(
                        "anyOf",
                        Document.of(List.of(
                                Document.of(value),
                                Document.of(Map.of("type", Document.of("null")))))))
                : Document.of(value);
        return JsonObjectSchema.builder()
                .description(memberDescription(member))
                .additionalProperties(additionalProperties)
                .build();
    }

    private static Document createUnionVariant(String memberName, SerializableShape memberSchema) {
        return Document.of(JsonObjectSchema.builder()
                .properties(Map.of(memberName, Document.of(memberSchema)))
                .required(List.of(memberName))
                .additionalProperties(Document.of(false))
                .build());
    }

    private static String memberDescription(Schema schema) {
        String description = null;
        var trait = schema.isMember()
                ? schema.getDirectTrait(TraitKey.DOCUMENTATION_TRAIT)
                : schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        if (trait != null) {
            description = trait.getValue();
        }
        if (schema.isMember()) {
            var targetDescription = memberDescription(schema.memberTarget());
            if (description != null && targetDescription != null) {
                description = appendSentences(description, targetDescription);
            } else if (targetDescription != null) {
                description = targetDescription;
            }
        }
        return description;
    }

    private static String createDescription(String serviceName, String operationName, Schema schema) {
        var documentation = schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        return documentation == null
                ? "This tool invokes %s API of %s.".formatted(operationName, serviceName)
                : documentation.getValue();
    }

    private static String appendSentences(String first, String second) {
        first = first.trim();
        if (!first.endsWith(".")) {
            first += ". ";
        }
        return first + second;
    }

    private static SerializableShape withDescription(SerializableShape schema, String description) {
        if (description == null) {
            return schema;
        }
        return switch (schema) {
            case JsonObjectSchema object -> object.toBuilder().description(description).build();
            case JsonOneOfSchema oneOf -> oneOf.toBuilder().description(description).build();
            default -> schema;
        };
    }
}
