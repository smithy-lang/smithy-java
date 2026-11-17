/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Base implementation of an {@link ApiOperation}.
 *
 * @param <I> operation input shape type
 * @param <O> operation output shape type
 */
public abstract class AbstractApiOperation<I extends SerializableStruct, O extends SerializableStruct>
        implements ApiOperation<I, O> {
    private final Schema schema;
    private final TypeRegistry errorRegistry;
    private final List<ShapeId> effectiveAuthSchemes;

    protected AbstractApiOperation(Schema schema) {
        this(schema, TypeRegistry.empty(), List.of());
    }

    protected AbstractApiOperation(Schema schema, TypeRegistry errorRegistry) {
        this(schema, errorRegistry, List.of());
    }

    protected AbstractApiOperation(Schema schema, List<ShapeId> effectiveAuthSchemes) {
        this(schema, TypeRegistry.empty(), effectiveAuthSchemes);
    }

    protected AbstractApiOperation(Schema schema, ShapeId effectiveAuthScheme) {
        this(schema, TypeRegistry.empty(), List.of(effectiveAuthScheme));
    }

    protected AbstractApiOperation(Schema schema, TypeRegistry errorRegistry, ShapeId effectiveAuthScheme) {
        this(schema, errorRegistry, List.of(effectiveAuthScheme));
    }

    protected AbstractApiOperation(Schema schema, TypeRegistry errorRegistry, List<ShapeId> effectiveAuthSchemes) {
        this.schema = schema;
        this.errorRegistry = errorRegistry;
        this.effectiveAuthSchemes = effectiveAuthSchemes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final ShapeBuilder<I> inputBuilder() {
        return (ShapeBuilder<I>) inputSchema().shapeBuilder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final ShapeBuilder<O> outputBuilder() {
        return (ShapeBuilder<O>) outputSchema().shapeBuilder();
    }

    @Override
    public final Schema schema() {
        return schema;
    }

    @Override
    public final TypeRegistry errorRegistry() {
        return errorRegistry;
    }

    @Override
    public final List<ShapeId> effectiveAuthSchemes() {
        return effectiveAuthSchemes;
    }

    @Override
    public List<Schema> errorSchemas() {
        return List.of();
    }

    @Override
    public Schema idempotencyTokenMember() {
        return null;
    }

    @Override
    public Schema inputStreamMember() {
        return null;
    }

    @Override
    public Schema outputStreamMember() {
        return null;
    }

    @Override
    public ApiService service() {
        return null;
    }
}
