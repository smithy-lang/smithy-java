/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import java.nio.file.Paths;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.client.settings.TestSettings;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Simple wrapper class used to execute the test Java codegen plugin for integration tests.
 */
public final class TestServerJavaClientCodegenRunner {
    private TestServerJavaClientCodegenRunner() {
        // Utility class does not have constructor
    }

    public static void main(String[] args) {
        JavaClientCodegenPlugin plugin = new JavaClientCodegenPlugin();
        Model model = Model.assembler(TestServerJavaClientCodegenRunner.class.getClassLoader())
                .discoverModels(TestServerJavaClientCodegenRunner.class.getClassLoader())
                .assemble()
                .unwrap();
        PluginContext context = PluginContext.builder()
                .fileManifest(FileManifest.create(Paths.get(System.getenv("output"))))
                .settings(
                        ObjectNode.builder()
                                .withMember("service", "smithy.java.codegen.server.test#TestService")
                                .withMember("namespace", "smithy.java.codegen.server.test")
                                .withMember(
                                        "transport",
                                        ObjectNode.builder()
                                                .withMember("http-java", ObjectNode.builder().build())
                                                .build())
                                .withMember("defaultPlugins",
                                        ArrayNode.fromStrings(TestClientPlugin.class.getCanonicalName()))
                                .withMember("defaultSettings",
                                        ArrayNode.fromStrings(TestSettings.class.getCanonicalName()))
                                .build())
                .model(model)
                .build();
        plugin.execute(context);
    }
}
