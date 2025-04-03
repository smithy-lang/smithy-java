/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DocumentException;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.dynamicclient.DynamicOperation;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.WrappedDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class ProxyService implements Service {

    private final DynamicClient dynamicClient;
    private final SchemaConverter schemaConverter;
    private final Map<String, Operation<WrappedDocument, WrappedDocument>> operations;
    private final TypeRegistry serviceErrorRegistry;
    private final Model model;
    private final ServiceShape service;
    private final List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> allOperations;
    private final Schema schema;

    private ProxyService(Builder builder, ServiceShape service, Model model) {
        this.model = model;
        this.service = service;
        DynamicClient.Builder clientBuilder = DynamicClient.builder()
                .service(service.getId())
                .model(model)
                .endpointResolver(EndpointResolver.staticEndpoint(builder.proxyEndpoint));
        if (builder.identityResolver != null) {
            clientBuilder.addIdentityResolver(builder.identityResolver);
        }
        if (builder.authScheme != null) {
            clientBuilder.authSchemeResolver(builder.authScheme);
        }
        if (builder.region != null) {
            clientBuilder.putConfig(RegionSetting.REGION, builder.region);
        }
        this.dynamicClient = clientBuilder.build();
        this.schemaConverter = new SchemaConverter(model);
        this.operations = new HashMap<>();
        var registryBuilder = TypeRegistry.builder();
        for (var e : service.getErrors()) {
            registerError(e, registryBuilder);
        }
        this.serviceErrorRegistry = registryBuilder.build();
        this.allOperations = new ArrayList<>();
        for (var operation : TopDownIndex.of(model).getContainedOperations(service.getId())) {
            String operationName = operation.getId().getName();
            var function =
                    new DynamicFunction(dynamicClient, operationName, schemaConverter, model, operation, service);
            Operation<WrappedDocument,
                    WrappedDocument> serverOperation = Operation.of(operationName,
                            function,
                            DynamicOperation.create(operation,
                                    schemaConverter,
                                    model,
                                    service,
                                    serviceErrorRegistry,
                                    this::registerError),
                            this);
            allOperations.add(serverOperation);
            operations.put(operationName, serverOperation);
        }
        this.schema = schemaConverter.getSchema(service);
    }

    private void registerError(ShapeId e, TypeRegistry.Builder registryBuilder) {
        var error = model.expectShape(e);
        var errorSchema = schemaConverter.getSchema(error);
        registryBuilder.putType(e,
                ModeledException.class,
                () -> new DocumentException.SchemaGuidedExceptionBuilder(service.getId(), errorSchema));
    }

    @Override
    public <I extends SerializableStruct,
            O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
        return (Operation<I, O>) operations.get(operationName);
    }

    @Override
    public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
        return allOperations;
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return serviceErrorRegistry;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String region;
        private Model model;
        private ShapeId service;
        private IdentityResolver<? extends Identity> identityResolver;
        private String proxyEndpoint;
        private AuthSchemeResolver authScheme;

        private Builder() {}

        public ProxyService build() {
            Objects.requireNonNull(model, "model is not set");
            Objects.requireNonNull(service, "service is not set");
            ServiceShape shape = model.expectShape(service, ServiceShape.class);

            return new ProxyService(this, shape, model);
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

        public Builder identityResolver(IdentityResolver<? extends Identity> identityResolver) {
            this.identityResolver = Objects.requireNonNull(identityResolver, "credentialsProvider is not set");
            return this;
        }

        public Builder proxyEndpoint(String proxyEndpoint) {
            this.proxyEndpoint = proxyEndpoint;
            return this;
        }

        public Builder authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authScheme = authSchemeResolver;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }
    }

    private record DynamicFunction(DynamicClient dynamicClient,
                                   String operation, 
                                   SchemaConverter schemaConverter,
                                   Model model,
                                   OperationShape operationShape,
                                   ServiceShape serviceShape) implements BiFunction<WrappedDocument, RequestContext, WrappedDocument> {

        @Override
            public WrappedDocument apply(WrappedDocument input, RequestContext requestContext) {
                return createWrappedDocument(operationShape.getOutput().get(), dynamicClient.call(operation, input));
            }

            private WrappedDocument createWrappedDocument(ToShapeId shape, Document value) {
                var schema = schemaConverter.getSchema(model.expectShape(shape.toShapeId()));
                if (value.type() != ShapeType.MAP && value.type() != ShapeType.STRUCTURE) {
                    throw new IllegalArgumentException("Document value must be a map or structure, found " + value.type());
                }
                return new WrappedDocument(schema, value, serviceShape.getId());
            }
        }

}
