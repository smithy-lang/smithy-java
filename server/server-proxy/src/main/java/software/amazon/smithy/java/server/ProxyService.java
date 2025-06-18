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
import java.util.function.UnaryOperator;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
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
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class ProxyService implements Service {

    public static final Context.Key<Document> PROXY_INPUT = Context.key("smithy.proxy.input");

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
        if (builder.proxyEndpoint != null) {
            clientBuilder.endpointResolver(EndpointResolver.staticEndpoint(builder.proxyEndpoint));
        }
        if (builder.userAgentAppId != null) {
            clientBuilder.putConfig(CallContext.APPLICATION_ID, builder.userAgentAppId);
        }
        if (builder.clientConfigurator != null) {
            clientBuilder = builder.clientConfigurator.apply(clientBuilder);
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
        var containedOperations = TopDownIndex.of(model).getContainedOperations(service.getId());

        var proxyOperations = new ArrayList<OperationShape>();
        var nonProxyOperations = new ArrayList<OperationShape>();

        // Server operations are expensive to create due to model-to-schema conversions.
        // To minimize overhead, we first separate operations into proxy and non-proxy types,
        // then only create non-proxy operations when an equivalent proxy operation doesn't exist.
        for (var operation : containedOperations) {
            if (operation.hasTrait(ProxyOperationTrait.class)) {
                proxyOperations.add(operation);
            } else {
                nonProxyOperations.add(operation);
            }
        }

        for (var operation : proxyOperations) {
            var proxyOperation = model.expectShape(
                    operation.expectTrait(ProxyOperationTrait.class).getDelegateOperation(),
                    OperationShape.class);
            String operationName = proxyOperation.getId().getName();
            var serverOperation = createServerOperation(
                    operation,
                    proxyOperation,
                    operationName,
                    true,
                    model,
                    service);
            addOperation(serverOperation, operationName);
        }

        for (var operation : nonProxyOperations) {
            String operationName = operation.getId().getName();

            if (!operations.containsKey(operationName)) {
                var serverOperation = createServerOperation(
                        operation,
                        operation,
                        operationName,
                        false,
                        model,
                        service);
                addOperation(serverOperation, operationName);
            }
        }
        this.schema = schemaConverter.getSchema(service);
    }

    private Operation<StructDocument, StructDocument> createServerOperation(
            OperationShape sourceOperation,
            OperationShape targetOperation,
            String operationName,
            boolean isProxy,
            Model model,
            ServiceShape service
    ) {

        var apiOperation = DynamicOperation.create(
                sourceOperation,
                schemaConverter,
                model,
                service,
                serviceErrorRegistry,
                (e, rb) -> registerError(e, service.getId(), rb, model));

        var outputSchema = schemaConverter.getSchema(model.expectShape(targetOperation.getOutputShape()));

        var function = new DynamicFunction(
                dynamicClient,
                targetOperation.getId().getName(),
                outputSchema,
                service,
                isProxy);

        return Operation.of(operationName, function, apiOperation, this);
    }

    // Helper method to add operations to collections
    private void addOperation(Operation<StructDocument, StructDocument> serverOperation, String operationName) {
        allOperations.add(serverOperation);
        operations.put(operationName, serverOperation);
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
        private String userAgentAppId;

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

        /**
         * Set the app ID that will be inserted into the "app" section of the user-agent header.
         *
         * @param userAgentAppId string that will be inserted into the "app" section of the user-agent header
         * @return the builder.
         */
        public Builder userAgentAppId(String userAgentAppId) {
            this.userAgentAppId = userAgentAppId;
            return this;
        }
    }

    private record DynamicFunction(
            DynamicClient dynamicClient,
            String operation,
            Schema outputSchema,
            ServiceShape serviceShape,
            boolean isProxy) implements BiFunction<StructDocument, RequestContext, StructDocument> {

        @Override
        public StructDocument apply(StructDocument input, RequestContext requestContext) {
            Document output;
            if (isProxy) {
                //We need to stash the original input because dynamic client will erase anything extra.
                var requestOverride = RequestOverrideConfig.builder()
                        .putConfig(PROXY_INPUT, input.getMember("additionalInput"))
                        .build();
                output = dynamicClient.call(operation, input, requestOverride);
            } else {
                output = dynamicClient.call(operation, input);
            }
            if (output.type() != ShapeType.MAP && output.type() != ShapeType.STRUCTURE) {
                throw new IllegalArgumentException("Document value must be a map or structure, found " + output.type());
            }
            return StructDocument.of(outputSchema, output, serviceShape.getId());
        }
    }

    private record OperationTarget(OperationShape source, OperationShape target) {}

}
