/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server.utils;
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import static software.amazon.smithy.model.node.Node.fromStrings;

import java.nio.file.Paths;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Simple wrapper class used to execute the test Java codegen plugin for integration tests.
 */
public final class TestJavaCodegenRunner {
    private TestJavaCodegenRunner() {
        // Utility class does not have constructor
    }

    public static void main(String[] args) {
        SmithyBuildPlugin plugin = new JavaCodegenPlugin();
        Model model = Model.assembler(TestJavaCodegenRunner.class.getClassLoader())
                .discoverModels(TestJavaCodegenRunner.class.getClassLoader())
                .assemble()
                .unwrap();
        var fileManifest = FileManifest.create(Paths.get(System.getenv("output")));
        execute(plugin,
                model,
                fileManifest,
                "smithy.java.mcp.test#TestService",
                "software.amazon.smithy.java.mcp.test");
        execute(
                plugin,
                model,
                fileManifest,
                "software.amazon.smithy.java.mcp.conformance#ConformanceService",
                "software.amazon.smithy.java.mcp.conformance");
    }

    private static void execute(
            SmithyBuildPlugin plugin,
            Model model,
            FileManifest fileManifest,
            String service,
            String namespace
    ) {
        PluginContext context = PluginContext.builder()
                .fileManifest(fileManifest)
                .settings(
                        ObjectNode.builder()
                                .withMember("service", service)
                                .withMember("namespace", namespace)
                                .withMember("modes", ArrayNode.fromStrings("server"))
                                .withMember("runtimeTraits",
                                        fromStrings("smithy.api#documentation",
                                                "smithy.api#examples",
                                                "smithy.ai#prompts",
                                                "smithy.mcp#oneOf"))
                                .build())
                .model(model)
                .build();
        plugin.execute(context);
    }
}
