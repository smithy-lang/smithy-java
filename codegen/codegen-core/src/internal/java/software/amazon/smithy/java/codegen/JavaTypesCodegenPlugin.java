/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Set;
import java.util.StringJoiner;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.metadata.ShapeClosure;
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

    // Id of the shape closure that drives types-only generation. Namespaced so it cannot collide
    // with a model shape id or a model-authored closure.
    private static final String CLOSURE_ID = "software.amazon.smithy.java.codegen#types";

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

        // Generate the data shapes selected by the settings as a shape closure, with no
        // primary service. The director resolves the closure's transitive data shapes.
        runner.shapeClosure(typesClosure(settings));
        runner.generateDataShapesOnly();
        runner.model(context.getModel());
        runner.integrationClass(JavaCodegenIntegration.class);
        DefaultTransforms.apply(runner, codegenSettings);
        runner.run();
        LOGGER.info("Successfully generated Java class files.");
    }

    /**
     * Builds the shape closure to generate from the configured selector, explicitly listed shapes,
     * and renames. Explicit shapes are folded into the selector as id-equality clauses and trait
     * definitions are excluded so only data shapes are generated.
     */
    private static ShapeClosure typesClosure(TypeCodegenSettings settings) {
        // Fold the explicit shapes into the configured selector as id-equality alternatives,
        // e.g. :is(<selector>, [id='ns#A'], [id='ns#B']).
        String base = settings.selector().toString();
        if (!settings.shapes().isEmpty()) {
            var joiner = new StringJoiner(", ", ":is(", ")");
            joiner.add(base);
            for (var shape : settings.shapes()) {
                joiner.add("[id='" + shape + "']");
            }
            base = joiner.toString();
        }
        return ShapeClosure.builder()
                .id(CLOSURE_ID)
                // Exclude trait definitions so only data shapes are generated.
                .includeBySelector(base + " :not([trait|trait])")
                .rename(settings.renames())
                .build();
    }
}
