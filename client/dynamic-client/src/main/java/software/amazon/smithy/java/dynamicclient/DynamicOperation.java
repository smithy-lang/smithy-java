/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class DynamicOperation implements ApiOperation<StructDocument, StructDocument> {

    private final ApiService service;
    private final Schema operationSchema;
    private final Schema inputSchema;
    private final Schema outputSchema;
    private final List<Schema> errorSchemas;
    private final TypeRegistry typeRegistry;
    private final List<ShapeId> effectiveAuthSchemes;

    DynamicOperation(
            ApiService service,
            Schema operationSchema,
            Schema inputSchema,
            Schema outputSchema,
            Set<Schema> errorSchemas,
            TypeRegistry typeRegistry,
            List<ShapeId> effectiveAuthSchemes
    ) {
        this.service = service;
        this.effectiveAuthSchemes = effectiveAuthSchemes;
        this.operationSchema = operationSchema;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.errorSchemas = List.copyOf(errorSchemas);
        this.typeRegistry = typeRegistry;
        validateStreaming(inputSchema);
        validateStreaming(outputSchema);
    }

    private static void validateStreaming(Schema schema) {
        for (var member : schema.members()) {
            if (member.hasTrait(TraitKey.STREAMING_TRAIT)) {
                throw new UnsupportedOperationException("DynamicClient does not support streaming: " + member);
            }
        }
    }

    @Override
    public Schema schema() {
        return operationSchema;
    }

    @Override
    public ApiService service() {
        return service;
    }

    @Override
    public Schema inputSchema() {
        return inputSchema;
    }

    @Override
    public Schema outputSchema() {
        return outputSchema;
    }

    @Override
    public ShapeBuilder<StructDocument> inputBuilder() {
        return SchemaConverter.createDocumentBuilder(inputSchema(), service.schema().id());
    }

    @Override
    public ShapeBuilder<StructDocument> outputBuilder() {
        return SchemaConverter.createDocumentBuilder(outputSchema(), service.schema().id());
    }

    @Override
    public TypeRegistry errorRegistry() {
        return typeRegistry;
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        return effectiveAuthSchemes;
    }

    @Override
    public List<Schema> errorSchemas() {
        return errorSchemas;
    }

    public static DynamicOperation create(
            OperationShape shape,
            SchemaConverter schemaConverter,
            Model model,
            ServiceShape service,
            TypeRegistry serviceErrorRegistry,
            BiConsumer<ShapeId, TypeRegistry.Builder> registerErrorCallback
    ) {
        var serviceSchema = schemaConverter.getSchema(service);
        ApiService apiService = () -> serviceSchema;
        return create(apiService, shape, schemaConverter, model, service, serviceErrorRegistry, registerErrorCallback);
    }

    public static DynamicOperation create(
            ApiService apiService,
            OperationShape shape,
            SchemaConverter schemaConverter,
            Model model,
            ServiceShape service,
            TypeRegistry serviceErrorRegistry,
            BiConsumer<ShapeId, TypeRegistry.Builder> registerErrorCallback
    ) {
        var operationSchema = schemaConverter.getSchema(shape);

        List<ShapeId> authSchemes = new ArrayList<>();
        for (var trait : ServiceIndex.of(model).getEffectiveAuthSchemes(service).values()) {
            authSchemes.add(trait.toShapeId());
        }

        var inputSchema = schemaConverter.getSchema(model.expectShape(shape.getInputShape()));
        var outputSchema = schemaConverter.getSchema(model.expectShape(shape.getOutputShape()));

        // Default to using the service registry.
        var registry = serviceErrorRegistry;

        var errorSchemas = new HashSet<Schema>();
        // Create a type registry that is able to deserialize errors using schemas.
        if (!shape.getErrorsSet().isEmpty()) {
            var registryBuilder = TypeRegistry.builder();
            for (var e : shape.getErrorsSet()) {
                registerErrorCallback.accept(e, registryBuilder);
                errorSchemas.add(schemaConverter.getSchema(model.expectShape(e)));
            }
            // Compose the operation errors with the service errors.
            registry = TypeRegistry.compose(registryBuilder.build(), serviceErrorRegistry);
        }

        return new DynamicOperation(
                apiService,
                operationSchema,
                inputSchema,
                outputSchema,
                Collections.unmodifiableSet(errorSchemas),
                registry,
                authSchemes);
    }
}
