/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

final class DynamicOperation implements ApiOperation<WrappedDocument, WrappedDocument> {

    private final ShapeId service;
    private final Schema operationSchema;
    private final Schema inputSchema;
    private final Schema outputSchema;
    private final Set<Schema> errorSchemas;
    private final TypeRegistry typeRegistry;
    private final List<ShapeId> effectiveAuthSchemes;

    public DynamicOperation(
            ShapeId service,
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
        this.errorSchemas = errorSchemas;
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
    public Schema inputSchema() {
        return inputSchema;
    }

    @Override
    public Schema outputSchema() {
        return outputSchema;
    }

    @Override
    public ShapeBuilder<WrappedDocument> inputBuilder() {
        return new SchemaGuidedDocumentBuilder(service, inputSchema());
    }

    @Override
    public ShapeBuilder<WrappedDocument> outputBuilder() {
        return new SchemaGuidedDocumentBuilder(service, outputSchema());
    }

    @Override
    public TypeRegistry errorRegistry() {
        return typeRegistry;
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        return effectiveAuthSchemes;
    }
}
