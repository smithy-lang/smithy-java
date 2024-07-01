/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.TopologicalIndex;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates a {@code SharedSchemas} utility class that contains all unattached schemas for the model.
 */
@SmithyInternalApi
public final class SharedSchemasGenerator
    implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    // Types that generate their own schemas
    private static final EnumSet<ShapeType> EXCLUDED_TYPES = EnumSet.of(
        ShapeType.RESOURCE,
        ShapeType.SERVICE,
        ShapeType.UNION,
        ShapeType.ENUM,
        ShapeType.INT_ENUM,
        ShapeType.STRUCTURE,
        ShapeType.MEMBER,
        ShapeType.OPERATION
    );

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        directive.context()
            .writerDelegator()
            .useFileWriter(
                getFilename(directive.settings()),
                CodegenUtils.getModelNamespace(directive.settings()),
                writer -> {
                    var common = getCommonShapes(directive.connectedShapes().values(), directive.model());
                    writer.pushState();
                    var template = """
                        /**
                         * Defines shared shapes across the model package that are not part of another code-generated type.
                         */
                        final class SharedSchemas {

                            ${schemas:C|}

                            private SharedSchemas() {}
                        }
                        """;
                    writer.putContext(
                        "schemas",
                        new SchemasGenerator(
                            writer,
                            common,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.context()
                        )
                    );
                    writer.write(template);
                    writer.popState();
                }
            );
    }

    private record SchemasGenerator(
        JavaWriter writer,
        Set<Shape> shapes,
        SymbolProvider symbolProvider,
        Model model,
        CodeGenerationContext context
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("schemaClass", Schema.class);
            for (var shape : shapes) {
                writer.write(
                    "static final ${schemaClass:T} $L = ${C}",
                    CodegenUtils.toSchemaName(shape),
                    new SchemaGenerator(
                        writer,
                        shape,
                        symbolProvider,
                        model,
                        context
                    )
                );
            }
            writer.popState();
        }
    }

    /**
     * Loops through service closure and finds all shapes that will not generate their own schemas
     *
     * @return shapes that need a shared schema definition
     */
    private static Set<Shape> getCommonShapes(Collection<Shape> connectedShapes, Model model) {
        Map<Shape, Integer> totalOrdering = new HashMap<>();

        int count = 0;
        for (Shape s : TopologicalIndex.of(model).getOrderedShapes()) {
            totalOrdering.put(s, count++);
        }

        // Add all recursive shapes last
        for (Shape s : TopologicalIndex.of(model).getRecursiveShapes()) {
            totalOrdering.put(s, count++);
        }

        return connectedShapes.stream()
            .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
            .filter(s -> !Prelude.isPreludeShape(s))
            .sorted(Comparator.comparing(totalOrdering::get))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getFilename(JavaCodegenSettings settings) {
        return String.format("./%s/model/SharedSchemas.java", settings.packageNamespace().replace(".", "/"));
    }
}
