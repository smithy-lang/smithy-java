/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import java.nio.file.Paths;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Simple wrapper class used to execute the test Java codegen plugin for integration tests.
 */
public final class TestJavaTypeCodegenRunner {
    private TestJavaTypeCodegenRunner() {
        // Utility class does not have constructor
    }

    public static void main(String[] args) {
        JavaTypeCodegenPlugin plugin = new JavaTypeCodegenPlugin();
        Model model = Model.assembler(TestJavaTypeCodegenRunner.class.getClassLoader())
            .discoverModels(TestJavaTypeCodegenRunner.class.getClassLoader())
            .assemble()
            .unwrap();
        PluginContext context = PluginContext.builder()
            .fileManifest(FileManifest.create(Paths.get(System.getenv("output"))))
            .settings(
                ObjectNode.builder()
                    .withMember("namespace", "smithy.java.codegen.server.test")
                    .build()
            )
            .model(model)
            .build();
        plugin.execute(context);
    }
}
