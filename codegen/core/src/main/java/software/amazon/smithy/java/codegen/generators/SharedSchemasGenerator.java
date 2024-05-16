/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.MemberShape;
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
                    var shapesToGenerate = getCommonShapes(directive.connectedShapes().values());
                    writer.write(
                        """
                            /**
                             * Defines shared shapes across the model package that are not part of another code-generated type.
                             */
                            final class SharedSchemas {

                                ${C|}

                                private SharedSchemas() {}
                            }
                            """,
                        writer.consumer(w -> this.generateSchemas(w, directive, shapesToGenerate))
                    );
                }
            );
    }

    private void generateSchemas(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Set<Shape> shapes
    ) {
        Set<Shape> deferred = deferredShapes(directive.model(), shapes);
        writer.pushState();
        writer.putContext("schemaClass", SdkSchema.class);
        for (var shape : shapes) {
            if (deferred.contains(shape)) {
                writer.write("static final ${schemaClass:T} $L;", CodegenUtils.toSchemaName(shape));
            } else {
                writer.write(
                    "static final ${schemaClass:T} $L = ${C}",
                    CodegenUtils.toSchemaName(shape),
                    new SchemaGenerator(
                        writer,
                        shape,
                        directive.symbolProvider(),
                        directive.model(),
                        directive.context()
                    )
                );
            }
        }
        writer.openBlock("static {", "}", () -> {
            writeDeferred(writer, directive, deferred);
        });
        writer.popState();
    }

    private void writeDeferred(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Set<Shape> shapes
    ) {
        Set<Shape> deferred = deferredShapes(directive.model(), shapes);
        for (var shape : shapes) {
            if (deferred.contains(shape)) {
                continue;
            }
            writer.write(
                "$L = ${C}",
                CodegenUtils.toSchemaName(shape),
                new SchemaGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.context()
                )
            );
        }

        if (!deferred.isEmpty()) {
            writeDeferred(writer, directive, deferred);
        }
    }

    private Set<Shape> deferredShapes(Model model, Set<Shape> shapes) {
        Set<Shape> deferred = new HashSet<>();
        for (var shape : shapes) {
            boolean isDeferred = shape.members()
                .stream()
                .map(MemberShape::getTarget)
                .map(model::expectShape)
                .anyMatch(shapes::contains);
            if (isDeferred) {
                deferred.add(shape);
            }
        }
        return deferred;
    }

    /**
     * Loops through service closure and finds all shapes that will not generate their own schemas
     *
     * @return shapes that need a shared schema definition
     */
    private static Set<Shape> getCommonShapes(Collection<Shape> connectedShapes) {
        return connectedShapes.stream()
            .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
            .filter(s -> !Prelude.isPreludeShape(s))
            .collect(Collectors.toSet());
    }

    private static String getFilename(JavaCodegenSettings settings) {
        return String.format("./%s/model/SharedSchemas.java", settings.packageNamespace().replace(".", "/"));
    }
}
