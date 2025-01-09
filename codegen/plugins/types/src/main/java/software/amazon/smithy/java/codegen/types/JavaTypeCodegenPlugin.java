/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.DefaultTransforms;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Plugin to execute Java type code generation.
 */
@SmithyInternalApi
public final class JavaTypeCodegenPlugin implements SmithyBuildPlugin {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaTypeCodegenPlugin.class);

    @Override
    public String getName() {
        return "java-type-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        LOGGER.info("Generating Java types from smithy model.");
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = TypeCodegenSettings.fromNode(context.getSettings());
        var codegenSettings = settings.codegenSettings();
        runner.settings(codegenSettings);
        runner.directedCodegen(new DirectedJavaTypeCodegen());
        runner.fileManifest(context.getFileManifest());
        runner.service(codegenSettings.service());

        // Add the synthetic service to the model
        var closure = getClosure(context.getModel(), settings);
        LOGGER.info("Found {} shapes in generation closure", closure.size());
        var model = SyntheticServiceTransform.transform(context.getModel(), closure, settings.renames());
        runner.model(model);
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, codegenSettings);
        runner.run();
        LOGGER.info("Successfully generated Java class files.");
    }

    private static Set<Shape> getClosure(Model model, TypeCodegenSettings settings) {
        Set<Shape> closure = new HashSet<>();
        settings.shapes()
                .stream()
                .map(model::expectShape)
                .forEach(closure::add);
        settings.selector()
                .shapes(model)
                .filter(s -> !s.isMemberShape())
                .filter(s -> !Prelude.isPreludeShape(s))
                .forEach(closure::add);

        // Filter out any shapes from this closure that are contained by any other shapes in the closure
        Walker walker = new Walker(model);
        Set<Shape> nested = new HashSet<>();
        for (Shape shape : closure) {
            nested.addAll(
                    walker.walkShapes(shape)
                            .stream()
                            .filter(s -> !shape.equals(s))
                            .filter(s -> !s.isMemberShape())
                            .filter(s -> !Prelude.isPreludeShape(s))
                            .collect(Collectors.toSet()));
        }
        closure.removeAll(nested);
        if (closure.isEmpty()) {
            throw new CodegenException("Could not generate types. No shapes found in closure");
        }
        LOGGER.info("Found {} shapes in generation closure.", closure.size());

        return closure;
    }
}
