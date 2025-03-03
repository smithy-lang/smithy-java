/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.DefaultTransforms;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.cli.generators.EntryPointGenerator;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;

public class JavaCliCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "cli-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        var manifest = context.getFileManifest();
        var model = context.getModel();
        var rootSettings = CliCodegenSettings.fromObjectNode(context.getSettings());

        // Generate all service models.
        List<Symbol> serviceSymbols = new ArrayList<>();
        for (var settings : rootSettings.settings()) {
            generateService(settings, manifest, model);
            serviceSymbols.add(getServiceSymbol(context.getModel(), settings));
        }

        // TODO: Move this stuff into an orchestrating class
        // Create a stripped-down context for code generation
        var codegenContext = new EntryPointGenerationContext(
                context.getModel(),
                rootSettings,
                context.getFileManifest(),
                serviceSymbols);

        // Get service entry point if applicable
        new EntryPointGenerator().accept(codegenContext);

        // Finally flush any remaining writers.
        codegenContext.writerDelegator().flushWriters();
    }

    private static Symbol getServiceSymbol(Model model, JavaCodegenSettings settings) {
        var serviceShape = model.expectShape(settings.service()).asServiceShape().orElseThrow();
        return new CliJavaSymbolProvider(model, serviceShape, settings.packageNamespace(), settings.name())
                .serviceShape(serviceShape);
    }

    private static void generateService(JavaCodegenSettings settings, FileManifest manifest, Model model) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();
        runner.settings(settings);
        runner.directedCodegen(new DirectedJavaCliCodegen());
        runner.fileManifest(manifest);
        runner.model(model);
        runner.service(settings.service());
        DefaultTransforms.apply(runner, settings);
        runner.integrationClass(JavaCodegenIntegration.class);
        runner.run();
    }
}
