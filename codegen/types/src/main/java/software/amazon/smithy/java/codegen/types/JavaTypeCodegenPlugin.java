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
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Plugin to execute Java type code generation.
 */
@SmithyUnstableApi
public class JavaTypeCodegenPlugin implements SmithyBuildPlugin {
    @Override
    public String getName() {
        return "java-type-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner = new CodegenDirector<>();

        var settings = JavaCodegenSettings.fromNode(
            SyntheticServiceTransformer.SYNTHETIC_SERVICE_ID,
            context.getSettings()
        );
        runner.settings(settings);
        runner.directedCodegen(new DirectedJavaTypeCodegen());
        runner.fileManifest(context.getFileManifest());
        runner.service(settings.service());
        var typeClosure = getTypeClosure(context.getModel(), settings);
        runner.model(SyntheticServiceTransformer.transform(context.getModel(), typeClosure));
        runner.integrationClass(JavaCodegenIntegration.class);
        runner.performDefaultCodegenTransforms();
        runner.run();
    }

    private Set<Shape> getTypeClosure(Model model, JavaCodegenSettings settings) {
        Set<Shape> shapes = new HashSet<>();
        settings.shapes().stream().map(model::expectShape).forEach(shapes::add);
        settings.selector()
            .shapes(model)
            .filter(s -> !s.isMemberShape())
            .filter(s -> !Prelude.isPreludeShape(s))
            .forEach(shapes::add);

        // Add all nested shapes within the closure of the selected shapes
        Walker walker = new Walker(model);
        Set<Shape> nested = new HashSet<>();
        for (Shape shape : shapes) {
            nested.addAll(
                walker.walkShapes(shape)
                    .stream()
                    .filter(s -> !s.isMemberShape())
                    .filter(s -> !Prelude.isPreludeShape(s))
                    .collect(Collectors.toSet())
            );
        }
        shapes.addAll(nested);

        return shapes;
    }
}
