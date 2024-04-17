/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.utils;

import java.nio.file.Paths;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;


/**
 * Simple wrapper class used to execute the test Java codegen plugin for integration tests.
 */
public final class TestJavaCodegenRunner {
    private TestJavaCodegenRunner() {
        // Utility class does not have constructor
    }

    public static void main(String[] args) {
        TestJavaCodegenPlugin plugin = new TestJavaCodegenPlugin();
        Model model = Model.assembler(TestJavaCodegenRunner.class.getClassLoader())
            .discoverModels(TestJavaCodegenRunner.class.getClassLoader())
            .assemble()
            .unwrap();
        System.out.println("WRITING TO : " + System.getenv("output"));
        PluginContext context = PluginContext.builder()
            .fileManifest(FileManifest.create(Paths.get(System.getenv("output"))))
            .settings(
                ObjectNode.builder()
                    .withMember("service", System.getenv("service"))
                    .withMember("namespace", System.getenv("namespace"))
                    .build()
            )
            .model(model)
            .build();
        plugin.execute(context);
    }
}
