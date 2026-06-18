/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.combined;

import java.nio.file.Paths;
import java.util.Objects;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Executes the Java codegen plugin in combined {@code [client, types]} mode for integration tests.
 *
 * <p>The model is loaded in isolation via {@code addImport} (rather than {@code discoverModels}) so
 * the combined closure's standalone-type scan is scoped to just this model's shapes.
 */
public final class TestCombinedModeCodegenRunner {
    private TestCombinedModeCodegenRunner() {
        // Utility class does not have constructor
    }

    public static void main(String[] args) {
        JavaCodegenPlugin plugin = new JavaCodegenPlugin();
        Model model = Model.assembler(TestCombinedModeCodegenRunner.class.getClassLoader())
                .addImport(Objects.requireNonNull(
                        TestCombinedModeCodegenRunner.class.getResource("combined-it.smithy")))
                .assemble()
                .unwrap();
        PluginContext context = PluginContext.builder()
                .fileManifest(FileManifest.create(Paths.get(System.getenv("output"))))
                .settings(
                        ObjectNode.builder()
                                .withMember("service", "smithy.java.codegen.combined.it#CombinedItService")
                                .withMember("namespace", "smithy.java.codegen.combined.it")
                                .withMember("modes", ArrayNode.fromStrings("client", "types"))
                                .build())
                .model(model)
                .build();
        plugin.execute(context);
    }
}
