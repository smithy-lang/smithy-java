/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
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
        var modelAssemble = new ModelAssembler().putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .addUnparsedModel("bundle.json", bundle.getModel())
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

        // mix in the generic arg members
        for (var op : model.getOperationShapes()) {
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
                b.addShape(op.toBuilder()
                        .input(additionalInputShape)
                        .build());
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
                    var input = shape.toBuilder();
                    for (var member : additionalInputShape.members()) {
                        input.addMember(member.toBuilder()
                                .id(ShapeId.from(input.getId().toString() + "$" + member.getMemberName()))
                                .build());
                    }
                    b.addShape(input.build());
                }
            }
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
}
