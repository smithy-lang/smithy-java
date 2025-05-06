/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DocumentException;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.dynamicclient.DynamicOperation;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
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
    private final Map<String, Operation<StructDocument, StructDocument>> operations;
    private final TypeRegistry serviceErrorRegistry;
    private final List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> allOperations;
    private final Schema schema;

    private ProxyService(Builder builder) {
        var model = builder.model;
        DynamicClient.Builder clientBuilder = DynamicClient.builder()
                .service(builder.service)
                .model(builder.model);
        if (builder.identityResolver != null) {
            clientBuilder.addIdentityResolver(builder.identityResolver);
        }
        if (builder.authScheme != null) {
            clientBuilder.authSchemeResolver(builder.authScheme);
        }
        if (builder.region != null) {
            clientBuilder.putConfig(RegionSetting.REGION, builder.region);
        }
        if (builder.clientConfigurator != null) {
            clientBuilder = builder.clientConfigurator.apply(clientBuilder);
        } else {
            clientBuilder.endpointResolver(EndpointResolver.staticEndpoint(builder.proxyEndpoint));
        }
        this.dynamicClient = clientBuilder.build();
        this.schemaConverter = new SchemaConverter(model);
        this.operations = new HashMap<>();
        var registryBuilder = TypeRegistry.builder();
        var service = model.expectShape(builder.service, ServiceShape.class);
        for (var e : service.getErrors()) {
            registerError(e, builder.service, registryBuilder, model);
        }
        this.serviceErrorRegistry = registryBuilder.build();
        this.allOperations = new ArrayList<>();
        for (var operation : TopDownIndex.of(model).getContainedOperations(service.getId())) {
            String operationName = operation.getId().getName();
            var function =
                    new DynamicFunction(dynamicClient,
                            operationName,
                            schemaConverter,
                            model,
                            operation,
                            service);
            Operation<StructDocument,
                    StructDocument> serverOperation = Operation.of(operationName,
                            function,
                            DynamicOperation.create(operation,
                                    schemaConverter,
                                    model,
                                    service,
                                    serviceErrorRegistry,
                                    (e, rb) -> registerError(e, builder.service, rb, model)),
                            this);
            allOperations.add(serverOperation);
            operations.put(operationName, serverOperation);
        }
        this.schema = schemaConverter.getSchema(service);
    }

    private void registerError(ShapeId e, ShapeId serviceId, TypeRegistry.Builder registryBuilder, Model model) {
        var error = model.expectShape(e);
        var errorSchema = schemaConverter.getSchema(error);
        registryBuilder.putType(e,
                ModeledException.class,
                () -> new DocumentException.SchemaGuidedExceptionBuilder(serviceId, errorSchema));
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
        private UnaryOperator<DynamicClient.Builder> clientConfigurator;

        private Builder() {}

        public ProxyService build() {
            Objects.requireNonNull(model, "model is not set");
            Objects.requireNonNull(service, "service is not set");

            return new ProxyService(this);
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

        public Builder clientConfigurator(UnaryOperator<DynamicClient.Builder> clientConfigurator) {
            this.clientConfigurator = clientConfigurator;
            return this;
        }
    }

    private record DynamicFunction(
            DynamicClient dynamicClient,
            String operation,
            SchemaConverter schemaConverter,
            Model model,
            OperationShape operationShape,
            ServiceShape serviceShape) implements BiFunction<StructDocument, RequestContext, StructDocument> {

        @Override
        public StructDocument apply(StructDocument input, RequestContext requestContext) {
            return createStructDocument(operationShape.getOutput().get(), dynamicClient.call(operation, input));
        }

        private StructDocument createStructDocument(ToShapeId shape, Document value) {
            var schema = schemaConverter.getSchema(model.expectShape(shape.toShapeId()));
            if (value.type() != ShapeType.MAP && value.type() != ShapeType.STRUCTURE) {
                throw new IllegalArgumentException("Document value must be a map or structure, found " + value.type());
            }
            return StructDocument.of(schema, value, serviceShape.getId());
        }
    }

    private static class NoOpClientProtocol<I, O> implements ClientProtocol<I, O> {

        private static final NoOpClientProtocol INSTANCE = new NoOpClientProtocol();

        private NoOpClientProtocol() {

        }

        @Override
        public ShapeId id() {
            return null;
        }

        @Override
        public Codec payloadCodec() {
            return null;
        }

        @Override
        public MessageExchange<I, O> messageExchange() {
            return null;
        }

        @Override
        public <I1 extends SerializableStruct, O1 extends SerializableStruct> I createRequest(
                ApiOperation<I1, O1> operation,
                I1 input,
                Context context,
                URI endpoint
        ) {
            return null;
        }

        @Override
        public I setServiceEndpoint(I request, Endpoint endpoint) {
            return null;
        }

        @Override
        public <I1 extends SerializableStruct, O1 extends SerializableStruct> CompletableFuture<O1> deserializeResponse(
                ApiOperation<I1, O1> operation,
                Context context,
                TypeRegistry errorRegistry,
                I request,
                O response
        ) {
            return null;
        }
    }
}
