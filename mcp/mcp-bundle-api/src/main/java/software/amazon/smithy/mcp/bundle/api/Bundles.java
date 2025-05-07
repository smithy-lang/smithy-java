/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.SmithyBundle;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

public class Bundles {

    private static final PluginProviders PLUGIN_PROVIDERS = PluginProviders.builder().build();

    private Bundles() {}

    public static Service getService(Bundle bundle) {
        if (bundle.type() != Bundle.Type.smithyBundle) {
            throw new IllegalArgumentException("Bundle is not a smithy bundle");
        }
        SmithyBundle smithyBundle = bundle.getValue();
        var model = getModel(smithyBundle);
        var plugin = PLUGIN_PROVIDERS.getPlugin(smithyBundle.getConfigType(), smithyBundle.getConfig());
        return ProxyService.builder()
                .model(model)
                .clientConfigurator(plugin::configureClient)
                .service(ShapeId.from(smithyBundle.getServiceName()))
                .build();
    }

    private static Model getModel(SmithyBundle bundle) {
        var modelAssemble = new ModelAssembler().putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .addUnparsedModel("bundle.json", bundle.getModel());
        var additionalInput = bundle.getAdditionalInput();
        if (additionalInput != null) {
            modelAssemble.addUnparsedModel("additionalInput.smithy", additionalInput.getModel());
            var model = modelAssemble.assemble().unwrap();
            var template = model.expectShape(ShapeId.from(additionalInput.getIdentifier())).asStructureShape().get();
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
        return modelAssemble.assemble().unwrap();
    }
}
