/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.client.JavaClientCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public abstract class AbstractCodegenFileTest {

    protected final MockManifest manifest = new MockManifest();
    protected final SmithyBuildPlugin plugin = new JavaClientCodegenPlugin();

    @BeforeEach
    public void setup() {
        var model = Model.assembler()
                .addImport(testFile())
                .assemble()
                .unwrap();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(settings())
                .model(model)
                .build();
        plugin.execute(context);
        assertFalse(manifest.getFiles().isEmpty());
    }

    protected abstract URL testFile();

    protected ObjectNode settings() {
        return ObjectNode.builder()
                .withMember("service", "smithy.java.codegen#TestService")
                .withMember("namespace", "test.smithy.codegen")
                .build();
    }

    protected String getFileStringForClass(String className) {
        var fileStringOptional = manifest.getFileString(
                Paths.get(String.format("/test/smithy/codegen/%s.java", className)));
        assertTrue(fileStringOptional.isPresent());
        return fileStringOptional.get();
    }
}
