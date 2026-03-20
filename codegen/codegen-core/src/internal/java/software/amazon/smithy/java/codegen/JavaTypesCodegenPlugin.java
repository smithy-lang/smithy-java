/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Types-only Java code generation plugin. Generates Java types from Smithy model shapes
 * without any client or server code.
 *
 * <p>This plugin is registered as {@code "internal-types-only"} and is used by modules
 * that only need type generation (e.g., framework-errors), avoiding dependencies on
 * client/server modules.
 *
 * <p>Configure via {@code smithy-build.json}:
 * <pre>{@code
 * {
 *   "plugins": {
 *     "internal-types-only": {
 *       "namespace": "com.example",
 *       "selector": "[id|namespace = 'com.example']"
 *     }
 *   }
 * }
 * }</pre>
 */
@SmithyInternalApi
public final class JavaTypesCodegenPlugin implements SmithyBuildPlugin {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaTypesCodegenPlugin.class);

    @Override
    public String getName() {
        return "internal-types-only";
    }

    @Override
    public void execute(PluginContext context) {
        LOGGER.info("Generating Java types from smithy model.");
        var modes = Set.of(CodegenMode.TYPES);

        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner =
                new CodegenDirector<>();

        var settings = TypeCodegenSettings.fromNode(context.getSettings());
        var codegenSettings = settings.codegenSettings();
        runner.settings(codegenSettings);
        runner.directedCodegen(new TypesDirectedJavaCodegen(modes));
        runner.fileManifest(context.getFileManifest());
        runner.service(codegenSettings.service());

        // Compute closure and create synthetic service
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
        for (var shapeId : settings.shapes()) {
            closure.add(model.expectShape(shapeId));
        }
        settings.selector()
                .shapes(model)
                .filter(s -> !s.isMemberShape())
                .filter(s -> !Prelude.isPreludeShape(s))
                .forEach(closure::add);

        if (closure.isEmpty()) {
            throw new CodegenException("Could not generate types. No shapes found in closure");
        }
        LOGGER.info("Found {} shapes in generation closure.", closure.size());

        return closure;
    }
}
