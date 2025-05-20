/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;

/**
 * A client that can call a service using a Smithy model directly without any codegen.
 *
 * <p>Input and output is provided as a {@link Document}. The contents of these documents are the captured, in-memory
 * Smithy data model. The format of the document must match the Smithy model and not any particular protocol
 * serialization. For example, use structure and member names defined in Smithy models rather than any particular
 * protocol representation like jsonName.
 *
 * <p>If an explicit protocol and transport are not provided to the builder, the builder will attempt to find
 * protocol and transport implementations on the classpath that match the protocol traits attached to the service.
 *
 * <p>This client has the following limitations:
 *
 * <ul>
 *     <li>No code generated types. You have to construct input and use output manually using document APIs.</li>
 *     <li>No support for streaming inputs or outputs.</li>
 *     <li>All errors are created as an {@link DocumentException} if the error is modeled, allowing document access
 *     to the modeled error contents. Other errors are deserialized as {@link CallException}.
 * </ul>
 */
public final class DynamicClient extends Client {

    private final ServiceShape service;
    private final Model model;
    private final ConcurrentMap<String, ApiOperation<StructDocument, StructDocument>> operations =
            new ConcurrentHashMap<>();
    private final SchemaConverter schemaConverter;
    private final Map<String, OperationShape> operationNames = new HashMap<>();
    private final TypeRegistry serviceErrorRegistry;

    private DynamicClient(Builder builder, SchemaConverter converter, ServiceShape shape, Model model) {
        super(builder);
        this.model = model;
        this.service = shape;
        this.schemaConverter = converter;

        // Create a lookup table of operation names to the operation shape IDs.
        for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
            operationNames.put(operation.getId().getName(), operation);
        }

        // Build and register service-wide errors.
        var registryBuilder = TypeRegistry.builder();
        for (var e : service.getErrors()) {
            registerError(e, registryBuilder);
        }
        this.serviceErrorRegistry = registryBuilder.build();
    }

    private void registerError(ShapeId e, TypeRegistry.Builder registryBuilder) {
        var error = model.expectShape(e);
        var errorSchema = schemaConverter.getSchema(error);
        registryBuilder.putType(e, ModeledException.class, () -> {
            return new DocumentException.SchemaGuidedExceptionBuilder(service.getId(), errorSchema);
        });
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
    public Document call(String operation) {
        return call(operation, Document.of(Map.of()));
    }

    /**
     * Call an operation with input.
     *
     * @param operation Operation name to call.
     * @param input Operation input as a document.
     * @return the output of the operation.
     */
    public Document call(String operation, Document input) {
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
    public Document call(String operation, Document input, RequestOverrideConfig overrideConfig) {
        try {
            return callAsync(operation, input, overrideConfig).join();
        } catch (CompletionException e) {
            throw unwrapAndThrow(e);
        }
    }

    /**
     * Call an operation, passing no input.
     *
     * @param operation Operation name to call.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> callAsync(String operation) {
        return callAsync(operation, Document.of(Map.of()));
    }

    /**
     * Call an operation with input.
     *
     * @param operation Operation name to call.
     * @param input Operation input as a document.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> callAsync(String operation, Document input) {
        return callAsync(operation, input, null);
    }

    /**
     * Call an operation with input and custom request override configuration.
     *
     * @param operation Operation name to call.
     * @param input Operation input as a document.
     * @param overrideConfig Override configuration for the request.
     * @return the output of the operation.
     */
    public CompletableFuture<Document> callAsync(
            String operation,
            Document input,
            RequestOverrideConfig overrideConfig
    ) {
        var apiOperation = getApiOperation(operation);
        var inputStruct = StructDocument.of(apiOperation.inputSchema(), input, service.getId());
        return call(inputStruct, apiOperation, overrideConfig).thenApply(Function.identity());
    }

    /**
     * Get an ApiOperation by name.
     *
     * @param name Name of the operation to get.
     * @return the operation.
     */
    public ApiOperation<StructDocument, StructDocument> getOperation(String name) {
        return getApiOperation(name);
    }

    /**
     * Create a {@link SerializableStruct} from a schema and document.
     *
     * @param shape Shape to mimic by the struct. The shape ID must be found in the model.
     * @param value Value to use as the struct. Must be a map or structure.
     * @return the serializable struct.
     */
    public SerializableStruct createStruct(ToShapeId shape, Document value) {
        var schema = schemaConverter.getSchema(model.expectShape(shape.toShapeId()));
        return StructDocument.of(schema, value, service.getId());
    }

    private ApiOperation<StructDocument, StructDocument> getApiOperation(String name) {
        return operations.computeIfAbsent(name, operation -> {
            var shape = operationNames.get(name);
            if (shape == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Operation '%s' not found in service '%s'",
                                name,
                                service.getId()));
            }

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
            if (!shape.getErrors().isEmpty()) {
                var registryBuilder = TypeRegistry.builder();
                for (var e : shape.getErrors()) {
                    registerError(e, registryBuilder);
                    errorSchemas.add(schemaConverter.getSchema(model.expectShape(e)));
                }
                // Compose the operation errors with the service errors.
                registry = TypeRegistry.compose(registryBuilder.build(), serviceErrorRegistry);
            }

            return new DynamicOperation(
                    config().service(),
                    operationSchema,
                    inputSchema,
                    outputSchema,
                    Collections.unmodifiableSet(errorSchemas),
                    registry,
                    authSchemes);
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

            if (configBuilder().protocol() == null) {
                autoDetectProtocol();
            }

            var converter = new SchemaConverter(model);

            // Create the schema for the service and put it in the config.
            var serviceSchema = converter.getSchema(shape);
            var apiService = new ApiService() {
                @Override
                public Schema schema() {
                    return serviceSchema;
                }
            };
            configBuilder().service(apiService);

            return new DynamicClient(this, converter, shape, model);
        }

        @SuppressWarnings("unchecked")
        private void autoDetectProtocol() {
            ServiceIndex serviceIndex = ServiceIndex.of(model);
            var protocols = serviceIndex.getProtocols(service);

            if (protocols.isEmpty()) {
                throw new IllegalArgumentException(
                        "No protocol() was provided, and not protocol definition traits "
                                + "were found on service " + service);
            }

            for (var protocolImpl : ServiceLoader.load(ClientProtocolFactory.class)) {
                if (protocols.containsKey(protocolImpl.id())) {
                    var settings = ProtocolSettings.builder().service(service).build();
                    protocol(protocolImpl.createProtocol(settings, protocols.get(protocolImpl.id())));
                    return;
                }
            }

            throw new IllegalArgumentException(
                    "Could not find any matching protocol implementations for the "
                            + "following protocol traits attached to service " + service
                            + ": " + protocols.keySet());
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
         * Returns the model that this client will use, or {@code null} if
         * {@link #model(Model)} has not yet been invoked.
         *
         * @return the client's model, or {@code null} if none is configured yet
         */
        public Model model() {
            return this.model;
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

        /**
         * Returns the ID of the service that will be called by the client, or {@code null} if {@link #service(ShapeId)} has not yet been invoked.
         *
         * @return the service shape ID, or {@code null} if none is configured yet
         */
        public ShapeId service() {
            return this.service;
        }
    }
}
