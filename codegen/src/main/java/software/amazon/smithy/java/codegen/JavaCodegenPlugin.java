/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Plugin to execute Java code generation.
 */
@SmithyUnstableApi
public class JavaCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "java-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner = new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(context.getSettings());
        runner.settings(settings);
        runner.directedCodegen(new DirectedJavaCodegen());
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        runner.model(context.getModel());
        runner.integrationClass(JavaCodegenIntegration.class);
        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();
        runner.run();
    }
}
