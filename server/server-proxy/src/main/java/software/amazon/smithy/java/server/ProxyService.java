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
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
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
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.modelbundle.api.BundlePlugin;
import software.amazon.smithy.modelbundle.api.PluginProviders;
import software.amazon.smithy.modelbundle.api.model.Bundle;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class ProxyService implements Service {
    private static final PluginProviders PLUGIN_PROVIDERS = PluginProviders.builder().build();

    private final DynamicClient dynamicClient;
    private final SchemaConverter schemaConverter;
    private final Map<String, Operation<StructDocument, StructDocument>> operations;
    private final TypeRegistry serviceErrorRegistry;
    private final Model model;
    private final List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> allOperations;
    private final Schema schema;
    private final BundlePlugin plugin;

    private static software.amazon.smithy.model.Model adapt(Builder builder) {
        if (builder.bundle == null || builder.bundle.getRequestArguments() == null) {
            return builder.model;
        }

        var args = builder.bundle.getRequestArguments();
        var model = new ModelAssembler()
                .addModel(builder.model)
                .addUnparsedModel("args.smithy", args.getModel().getValue())
                .assemble()
                .unwrap();
        var template = model.expectShape(ShapeId.from(args.getIdentifier()))
                .asStructureShape()
                .get();
        var b = model.toBuilder();

        // mix in the generic arg members
        for (var op : model.getOperationShapes()) {
            var input = model.expectShape(op.getInput().get(), StructureShape.class).toBuilder();
            for (var member : template.members()) {
                input.addMember(member.toBuilder()
                        .id(ShapeId.from(input.getId().toString() + "$" + member.getMemberName()))
                        .build());
            }
            b.addShape(input.build());
        }

        for (var service : model.getServiceShapes()) {
            b.addShape(service.toBuilder()
                    // trim the endpoint rules because they're huge and we don't need them
                    .removeTrait(ShapeId.from("smithy.rules#endpointRuleSet"))
                    .removeTrait(ShapeId.from("smithy.rules#endpointTests"))
                    .build());
        }

        return b.build();
    }

    private ProxyService(Builder builder) {
        this.model = adapt(builder);
        DynamicClient.Builder clientBuilder = DynamicClient.builder()
                .service(builder.service)
                .model(model);
        if (builder.bundle != null) {
            this.plugin = PLUGIN_PROVIDERS.getProvider(builder.bundle.getConfigType(), builder.bundle.getConfig());
            clientBuilder.endpointResolver(EndpointResolver.staticEndpoint("http://placeholder"));
        } else {
            this.plugin = null;
            // TODO: render this as a bundle
            clientBuilder.endpointResolver(EndpointResolver.staticEndpoint(builder.proxyEndpoint));
            if (builder.identityResolver != null) {
                clientBuilder.addIdentityResolver(builder.identityResolver);
            }
            if (builder.authScheme != null) {
                clientBuilder.authSchemeResolver(builder.authScheme);
            }
            if (builder.region != null) {
                clientBuilder.putConfig(RegionSetting.REGION, builder.region);
            }
        }
        this.dynamicClient = clientBuilder.build();
        this.schemaConverter = new SchemaConverter(model);
        this.operations = new HashMap<>();
        var registryBuilder = TypeRegistry.builder();
        var service = model.expectShape(builder.service, ServiceShape.class);
        for (var e : service.getErrors()) {
            registerError(e, builder.service, registryBuilder);
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
                            service,
                            plugin);
            Operation<StructDocument,
                    StructDocument> serverOperation = Operation.of(operationName,
                            function,
                            DynamicOperation.create(operation,
                                    schemaConverter,
                                    model,
                                    service,
                                    serviceErrorRegistry,
                                    (e, rb) -> registerError(e, builder.service, rb)),
                            this);
            allOperations.add(serverOperation);
            operations.put(operationName, serverOperation);
        }
        this.schema = schemaConverter.getSchema(service);
    }

    private void registerError(ShapeId e, ShapeId serviceId, TypeRegistry.Builder registryBuilder) {
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
        private Bundle bundle;

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

        public Builder bundle(Bundle bundle) {
            this.bundle = bundle;
            this.model = new ModelAssembler()
                    .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                    .addUnparsedModel("bundle.json", bundle.getModel().getValue())
                    // synthetic members may cause certain validations to fail, but it's ok for this application
                    .disableValidation()
                    .assemble()
                    .unwrap();
            this.service = ShapeId.from(bundle.getServiceName());
            return this;
        }
    }

    private record DynamicFunction(
            DynamicClient dynamicClient,
            String operation,
            SchemaConverter schemaConverter,
            Model model,
            OperationShape operationShape,
            ServiceShape serviceShape,
            BundlePlugin plugin) implements BiFunction<StructDocument, RequestContext, StructDocument> {

        @Override
        public StructDocument apply(StructDocument input, RequestContext requestContext) {
            RequestOverrideConfig bundleSettings = null;
            if (plugin != null) {
                bundleSettings = plugin.buildOverride(input).build();
            }
            return createStructDocument(operationShape.getOutput().get(),
                    dynamicClient.call(operation, input, bundleSettings));
        }

        private StructDocument createStructDocument(ToShapeId shape, Document value) {
            var schema = schemaConverter.getSchema(model.expectShape(shape.toShapeId()));
            if (value.type() != ShapeType.MAP && value.type() != ShapeType.STRUCTURE) {
                throw new IllegalArgumentException("Document value must be a map or structure, found " + value.type());
            }
            return StructDocument.of(schema, value, serviceShape.getId());
        }
    }

}
