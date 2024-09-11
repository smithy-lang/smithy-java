/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.generators;

import java.nio.file.Paths;
import java.util.Objects;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.client.JavaClientCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ClientProtocolTestGenerator {
    public static void main(String[] args) {
        JavaClientCodegenPlugin plugin = new JavaClientCodegenPlugin();
        Model model = Model.assembler(ClientProtocolTestGenerator.class.getClassLoader())
            .discoverModels(ClientProtocolTestGenerator.class.getClassLoader())
            .assemble()
            .unwrap();
        var serviceId = ShapeId.from(Objects.requireNonNull(System.getenv("service")));
        PluginContext context = PluginContext.builder()
            .fileManifest(FileManifest.create(Paths.get(System.getenv("output"))))
            .settings(
                ObjectNode.builder()
                    .withMember("service", serviceId.toString())
                    .withMember("namespace", serviceId.getNamespace())
                    .build()
            )
            .model(model)
            .build();
        plugin.execute(context);
    }

}
