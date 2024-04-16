/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.EnumSet;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.loader.Prelude;
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
                directive.settings().packageNamespace() + ".model",
                writer -> {
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
                        writer.consumer(w -> this.generatedSchemas(w, directive))
                    );
                }
            );
    }

    private void generatedSchemas(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        // Loop through service closure and find all shapes that will not generate their own schemas
        directive.connectedShapes()
            .values()
            .stream()
            .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
            .filter(s -> !Prelude.isPreludeShape(s))
            .forEach(
                s -> new SchemaGenerator(writer, s, directive.symbolProvider(), directive.model(), directive.context())
                    .run()
            );
    }

    private static String getFilename(JavaCodegenSettings settings) {
        return String.format("./%s/model/SharedSchemas.java", settings.packageNamespace().replace(".", "/"));
    }
}
