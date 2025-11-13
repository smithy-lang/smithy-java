/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.plugins.DetectProtocolPlugin;
import software.amazon.smithy.java.dynamicclient.settings.ModelSetting;
import software.amazon.smithy.java.dynamicclient.settings.ServiceIdSetting;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.model.Model;
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
    private final Schema serviceSchema;
    private final ApiService apiService;

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
        for (var e : service.getErrorsSet()) {
            registerError(e, registryBuilder);
        }
        this.serviceErrorRegistry = registryBuilder.build();

        // Create the ApiService.
        serviceSchema = schemaConverter.getSchema(service);
        apiService = () -> serviceSchema;
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
        var apiOperation = getApiOperation(operation);
        var inputStruct = StructDocument.of(apiOperation.inputSchema(), input, service.getId());
        return call(inputStruct, apiOperation, overrideConfig);
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

            return DynamicOperation.create(
                    apiService,
                    shape,
                    schemaConverter,
                    model,
                    service,
                    serviceErrorRegistry,
                    this::registerError);
        });
    }

    /**
     * Builder used to create a DynamicClient.
     */
    public static final class Builder extends Client.Builder<DynamicClient, Builder>
            implements ModelSetting<Builder>, ServiceIdSetting<Builder> {

        private Builder() {}

        @Override
        public DynamicClient build() {
            var model = configBuilder().context().expect(MODEL);

            // This stuff really can't be a plugin.
            var service = serviceId();
            ServiceShape shape;
            if (service != null) {
                shape = model.expectShape(service, ServiceShape.class);
            } else {
                // Attempt to auto-detect the service IFF a single service is in the model.
                Set<ServiceShape> serviceShapes = model.getServiceShapes();
                if (serviceShapes.size() == 1) {
                    shape = serviceShapes.iterator().next();
                    putConfig(SERVICE_ID, shape.getId());
                } else if (serviceShapes.size() > 1) {
                    throw new NullPointerException(
                            "No `service` set, and the model contains multiple services: " + serviceShapes);
                } else {
                    throw new NullPointerException("`service` is not set, and the model contains no services");
                }
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

            // Detecting a transport happens first when building a client. But we need to detect a protocol before
            // event that happens. So do that here manually.
            if (configBuilder().pluginPredicate().test(DetectProtocolPlugin.INSTANCE)) {
                DetectProtocolPlugin.INSTANCE.configureClient(configBuilder());
            }

            return new DynamicClient(this, converter, shape, model);
        }

        /**
         * Returns the model that this client will use, or {@code null} if
         * {@link #model(Model)} has not yet been invoked.
         *
         * @return the client's model, or {@code null} if none is configured yet
         */
        public Model model() {
            return configBuilder().context().get(MODEL);
        }

        /**
         * Returns the ID of the service that will be called by the client.
         *
         * @return the service shape ID, or {@code null} if none is configured yet
         */
        public ShapeId serviceId() {
            return configBuilder().context().get(SERVICE_ID);
        }

        @Deprecated
        public ShapeId service() {
            return serviceId();
        }
    }
}
