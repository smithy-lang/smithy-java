/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.server.ProxyOperationTrait;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.modelbundle.api.model.SmithyBundle;

public final class ModelBundles {

    private ModelBundles() {}

    private static final PluginProviders PLUGIN_PROVIDERS = PluginProviders.builder().build();

    public static Service getService(SmithyBundle smithyBundle) {
        var model = getModel(smithyBundle);
        var plugin = PLUGIN_PROVIDERS.getPlugin(smithyBundle.getConfigType(), smithyBundle.getConfig());
        return ProxyService.builder()
                .model(model)
                .clientConfigurator(plugin::configureClient)
                .service(ShapeId.from(smithyBundle.getServiceName()))
                .userAgentAppId("mcp-proxy")
                .build();
    }

    private static Model getModel(SmithyBundle bundle) {
        // TODO: model the type in the returned bundle
        var suffix = bundle.getModel().startsWith("$version") ? "smithy" : "json";
        var modelAssemble = new ModelAssembler().putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .addUnparsedModel("bundle." + suffix, bundle.getModel())
                .disableValidation();
        var additionalInput = bundle.getAdditionalInput();
        Model model;
        StructureShape additionalInputShape = null;
        if (additionalInput != null) {
            modelAssemble.addUnparsedModel("additionalInput.smithy", additionalInput.getModel());
            model = modelAssemble.assemble().unwrap();
            additionalInputShape =
                    model.expectShape(ShapeId.from(additionalInput.getIdentifier())).asStructureShape().get();

        } else {
            model = modelAssemble.assemble().unwrap();
        }
        var b = model.toBuilder();

        var serviceShape = model.expectShape(ShapeId.from(bundle.getServiceName()), ServiceShape.class);

        // mix in the generic arg members
        var serviceBuilder = serviceShape.toBuilder();
        for (var opId : serviceShape.getAllOperations()) {
            var op = model.expectShape(opId, OperationShape.class);
            boolean skipOperation = false;
            if (op.getOutput().isPresent()) {
                for (var member : model.expectShape(op.getOutputShape(), StructureShape.class).members()) {
                    if (model.expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                        b.removeShape(op.toShapeId());
                        skipOperation = true;
                        break;
                    }
                }
            }

            if (skipOperation) {
                continue;
            }

            if (op.getInput().isEmpty() && additionalInputShape != null) {
                addProxyOperationWithAdditionalInput(op, additionalInputShape, b, serviceBuilder, model);
            } else {
                var shape = model.expectShape(op.getInputShape(), StructureShape.class);
                for (var member : shape.members()) {
                    if (model.expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                        b.removeShape(op.toShapeId());
                        skipOperation = true;
                        break;
                    }
                }

                if (skipOperation) {
                    continue;
                }

                if (additionalInputShape != null) {
                    addProxyOperationWithAdditionalInput(op, additionalInputShape, b, serviceBuilder, model);
                }
            }
        }

        b.addShape(serviceBuilder
                // trim the endpoint rules because they're huge and we don't need them
                .removeTrait(ShapeId.from("smithy.rules#endpointRuleSet"))
                .removeTrait(ShapeId.from("smithy.rules#endpointTests"))
                .build());
        return b.build();
    }

    private static void addProxyOperationWithAdditionalInput(
            OperationShape op,
            StructureShape additionalInput,
            Model.Builder builder,
            ServiceShape.Builder serviceBuilder,
            Model model
    ) {
        var input = op.getInput();
        StructureShape finalInput;
        if (op.getInput().isEmpty()) {
            finalInput = additionalInput;
        } else {
            var inputBuilder = model.expectShape(input.get(), StructureShape.class).toBuilder();
            inputBuilder.addMember(MemberShape.builder()
                    .id(ShapeId.from(inputBuilder.getId().toString() + "$additionalInput"))
                    .target(additionalInput.getId())
                    .build());
            finalInput = inputBuilder.id(ShapeId.from(inputBuilder.getId().toString()) + "Proxy").build();
        }
        builder.addShape(finalInput);
        var newOperation = op.toBuilder()
                .id(ShapeId.from(op.getId().toString() + "Proxy"))
                .input(finalInput)
                .output(op.getOutputShape())
                .addTrait(new ProxyOperationTrait(op.getId()))
                .build();
        builder.addShape(newOperation);
        serviceBuilder.addOperation(newOperation).build();
    }
}
