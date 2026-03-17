/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Set;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.writer.JavaWriter;

public class TestJavaCodegenPlugin implements SmithyBuildPlugin {

    public CodeGenerationContext capturedContext;
    @Override
    public String getName() {
        return "test-java-codegen-core";
    }

    @Override
    public void execute(PluginContext context) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(context.getSettings());
        runner.settings(settings);
        DirectedJavaCodegen directedCodegen = new DirectedJavaCodegen(Set.of(CodegenMode.CLIENT, CodegenMode.SERVER));
        runner.directedCodegen(directedCodegen);
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        runner.model(context.getModel());
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, settings);
        runner.run();
        this.capturedContext = directedCodegen.context;
    }
}
