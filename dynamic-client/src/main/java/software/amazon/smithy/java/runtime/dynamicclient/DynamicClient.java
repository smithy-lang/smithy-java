/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.dynamicclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A client that can call a service using a Smithy model directly without any codegen.
 *
 * <p>Input and output is provided as a {@link Document}. The contents of these documents are the captured, in-memory
 * Smithy data model. The format of the document must match the Smithy model and not any particular protocol
 * serialization. For example, use structure and member names defined in Smithy models rather than any particular
 * protocol representation like jsonName.
 *
 * <p>This client has the following limitations:
 *
 * <ul>
 *     <li>No code generated types. You have to construct input and use output manually using document APIs.</li>
 *     <li>No support for streaming inputs or outputs.</li>
 * </ul>
 */
public final class DynamicClient extends Client {

    private final ServiceShape service;
    private final Model model;
    private final ConcurrentMap<String, ApiOperation<WrappedDocument, WrappedDocument>> operations = new ConcurrentHashMap<>();
    private final SchemaConverter schemaConverter;
    private final Map<String, OperationShape> operationNames = new HashMap<>();

    private DynamicClient(Builder builder, ServiceShape shape, Model model) {
        super(builder);
        this.model = model;
        this.service = shape;
        this.schemaConverter = new SchemaConverter(model);

        // Create a lookup table of operation names to the operation shape IDs.
        for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
            operationNames.put(operation.getId().getName(), operation);
        }
    }

    /**
     * Returns a builder used to create a DynamicClient.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Call an operation, passing no input.
     *
     * @param operation Operation name to call.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> call(String operation) {
        return call(operation, Document.createStringMap(Map.of()), null);
    }

    /**
     * Call an operation with input.
     *
     * @param operation Operation name to call.
     * @param input Operation input as a document.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> call(String operation, Document input) {
        return call(operation, input, null);
    }

    /**
     * Call an operation with input and custom request override configuration.
     *
     * @param operation Operation name to call.
     * @param input Operation input as a document.
     * @param overrideConfig Override configuration for the request.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> call(String operation, Document input, RequestOverrideConfig overrideConfig) {
        var apiOperation = getApiOperation(operation);
        var inputStruct = new WrappedDocument(apiOperation.inputSchema(), input);
        return call(inputStruct, apiOperation, overrideConfig).thenApply(Function.identity());
    }

    private ApiOperation<WrappedDocument, WrappedDocument> getApiOperation(String name) {
        return operations.computeIfAbsent(name, operation -> {
            var shape = operationNames.get(name);
            if (shape == null) {
                throw new IllegalArgumentException(
                    String.format(
                        "Operation '%s' not found in service '%s'",
                        name,
                        service.getId()
                    )
                );
            }

            var operationSchema = schemaConverter.getSchema(shape);
            var typeRegistry = TypeRegistry.builder().build();

            List<ShapeId> authSchemes = new ArrayList<>();
            for (var trait : ServiceIndex.of(model).getEffectiveAuthSchemes(service).values()) {
                authSchemes.add(trait.toShapeId());
            }

            var inputSchema = schemaConverter.getSchema(model.expectShape(shape.getInputShape()));
            var outputSchema = schemaConverter.getSchema(model.expectShape(shape.getOutputShape()));
            return new DynamicOperation(operationSchema, inputSchema, outputSchema, typeRegistry, authSchemes);
        });
    }

    /**
     * Builder used to create a DynamicClient.
     */
    public static final class Builder extends Client.Builder<DynamicClient, Builder> {

        private Model model;
        private ShapeId service;

        private Builder() {}

        @Override
        public DynamicClient build() {
            Objects.requireNonNull(model, "model is not set");
            Objects.requireNonNull(service, "service is not set");
            ServiceShape shape = model.expectShape(service, ServiceShape.class);
            return new DynamicClient(this, shape, model);
        }

        /**
         * <strong>Required</strong>: Set the Smithy model to use with the client.
         *
         * <p>This model <em>MUST</em> contain the service shape referenced in {@link #service(ShapeId)}.
         *
         * <pre>{@code
         * var model = Model.assembler().discoverModels().assemble().unwrap();
         * builder.model(model);
         * }</pre>
         *
         * @param model Model to use when calling the service.
         * @return the builder.
         */
        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        /**
         * <stong>Required</stong>: Set the service shape ID to call.
         *
         * <p>The given shape ID <em>MUST</em> be present in the model given in {@link #model(Model)}.
         *
         * @param service Service shape in the model to call with the dynamic client.
         * @return the builder.
         */
        public Builder service(ShapeId service) {
            this.service = service;
            return this;
        }
    }
}
