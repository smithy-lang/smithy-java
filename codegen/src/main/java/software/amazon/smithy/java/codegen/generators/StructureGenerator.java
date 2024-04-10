/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class StructureGenerator
    implements Consumer<GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            writer.write(
                """
                    public final class $T implements $T {
                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}
                    }
                    """,
                directive.symbol(),
                SerializableShape.class,
                writer.consumer(w -> w.writeIdString(shape)),
                new SchemaGenerator(writer, directive.shape(), directive.symbolProvider(), directive.model()),
                new PropertyGenerator(writer, shape, directive.symbolProvider()),
                new ConstructorGenerator(writer, shape, directive.symbolProvider()),
                new GetterGenerator(writer, shape, directive.symbolProvider(), directive.model()),
                writer.consumer(JavaWriter::writeToString),
                new EqualsGenerator(writer, directive.shape(), directive.symbolProvider()),
                new HashCodeGenerator(writer, directive.shape(), directive.symbolProvider()),
                new SerializerGenerator(writer),
                new BuilderGenerator(writer, shape, directive.symbolProvider(), directive.model())
            );
            writer.popState();
        });
    }
}
