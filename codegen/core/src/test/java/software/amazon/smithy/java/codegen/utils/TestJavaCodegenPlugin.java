/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.utils;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.transforms.RemoveDeprecatedShapesTransformer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;

public class TestJavaCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "test-java-codegen-core";
    }

    @Override
    public void execute(PluginContext context) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner = new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(context.getSettings());
        runner.settings(settings);
        runner.directedCodegen(new TestJavaCodegen());
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        // Filter out any deprecated shapes
        var model = RemoveDeprecatedShapesTransformer.transform(context.getModel(), settings);
        runner.model(model);
        runner.integrationClass(JavaCodegenIntegration.class);
        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();
        runner.run();
    }
}
