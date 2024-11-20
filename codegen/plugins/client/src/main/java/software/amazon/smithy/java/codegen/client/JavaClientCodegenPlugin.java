/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.DefaultTransforms;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Plugin to execute Java client code generation.
 */
@SmithyInternalApi
public final class JavaClientCodegenPlugin implements SmithyBuildPlugin {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaClientCodegenPlugin.class);

    @Override
    public String getName() {
        return "java-client-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner = new CodegenDirector<>();
        var settings = JavaCodegenSettings.fromNode(context.getSettings());
        LOGGER.info("Generating Smithy-Java client for service [{}]...", settings.service());
        runner.settings(settings);
        runner.directedCodegen(new DirectedJavaClientCodegen());
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        runner.model(context.getModel());
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, settings);
        runner.run();
        LOGGER.info("Smithy-Java client code generation complete");
    }
}
