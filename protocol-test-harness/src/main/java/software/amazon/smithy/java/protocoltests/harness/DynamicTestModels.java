/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.function.Supplier;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.dynamicclient.DocumentException;
import software.amazon.smithy.java.dynamicclient.DynamicOperation;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Builds document-backed ({@code DynamicClient}) operation and shape models for the protocol test harness, mirroring
 * what {@code DynamicClient} does at runtime. This lets client protocol tests run a second time through the untyped
 * document serialization/deserialization path in addition to the codegen path.
 *
 * <p>One instance is created per service under test and shared across all of that service's operations so the
 * underlying {@link SchemaConverter} schema cache is reused.
 */
final class DynamicTestModels {

    private final Model model;
    private final ServiceShape service;
    private final SchemaConverter schemaConverter;
    private final ApiService apiService;
    private final TypeRegistry serviceErrorRegistry;

    DynamicTestModels(Model model, ServiceShape service) {
        this.model = model;
        // Resolve the service from this model so its shapes/traits come from the same (dynamic) model instance.
        this.service = model.expectShape(service.getId(), ServiceShape.class);
        this.schemaConverter = new SchemaConverter(model);
        var serviceSchema = schemaConverter.getSchema(service);
        this.apiService = () -> serviceSchema;

        // Register service-wide errors the same way DynamicClient does.
        var registryBuilder = TypeRegistry.builder();
        for (var errorId : this.service.getErrorsSet()) {
            registerError(errorId, registryBuilder);
        }
        this.serviceErrorRegistry = registryBuilder.build();
    }

    /**
     * Create the document-backed {@link ApiOperation} for an operation, equivalent to what {@code DynamicClient}
     * resolves internally. The operation is resolved from this instance's own model so its input/output shapes use
     * the original (un-renamed) shape IDs.
     */
    ApiOperation<?, ?> createOperation(ShapeId operationId) {
        var shape = model.expectShape(operationId, OperationShape.class);
        return DynamicOperation.create(
                apiService,
                shape,
                schemaConverter,
                model,
                service,
                serviceErrorRegistry,
                this::registerError);
    }

    /**
     * Create a document-backed builder supplier for an error structure (resolved from this instance's model). This
     * builds a {@link DocumentException} (the same type the dynamic client deserializes errors into) so the expected
     * and actual values compare as like types.
     */
    Supplier<ShapeBuilder<? extends SerializableStruct>> createErrorBuilder(ShapeId errorId) {
        var schema = schemaConverter.getSchema(model.expectShape(errorId));
        return () -> new DocumentException.SchemaGuidedExceptionBuilder(service.getId(), schema);
    }

    private void registerError(ShapeId errorId, TypeRegistry.Builder registryBuilder) {
        var error = model.expectShape(errorId);
        var errorSchema = schemaConverter.getSchema(error);
        registryBuilder.putType(
                errorId,
                ModeledException.class,
                () -> new DocumentException.SchemaGuidedExceptionBuilder(service.getId(), errorSchema));
    }
}
