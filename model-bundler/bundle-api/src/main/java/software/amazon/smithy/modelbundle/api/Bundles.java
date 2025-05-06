/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.modelbundle.api.model.Bundle;

public class Bundles {

    private static final PluginProviders PLUGIN_PROVIDERS = PluginProviders.builder().build();

    private Bundles() {}

    public static ProxyService getProxyService(Bundle bundle) {
        var model = getModel(bundle);
        var plugin = PLUGIN_PROVIDERS.getPlugin(bundle.getConfigType(), bundle.getConfig());
        return ProxyService.builder()
                .model(model)
                .clientConfigurator(plugin::configureClient)
                .build();
    }

    private static Model getModel(Bundle bundle) {
        var modelAssemble = new ModelAssembler().putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .addUnparsedModel("bundle.json", bundle.getModel().getValue());
        var args = bundle.getRequestArguments();
        if (args != null) {
            modelAssemble.addUnparsedModel("args.smithy", bundle.getRequestArguments().getModel().getValue());
            var model = modelAssemble.assemble().unwrap();
            var template = model.expectShape(ShapeId.from(args.getIdentifier())).asStructureShape().get();
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

    public static BundlePlugin getBundlePlugin(Bundle bundle) {
        return PLUGIN_PROVIDERS.getPlugin(bundle.getConfigType(), bundle.getConfig());
    }
}
