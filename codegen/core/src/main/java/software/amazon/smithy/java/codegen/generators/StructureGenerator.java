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
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class StructureGenerator
    implements Consumer<GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            writer.putContext("shape", directive.symbol());
            writer.putContext("serializableStruct", SerializableStruct.class);
            writer.putContext("id", writer.consumer(w -> w.writeIdString(shape)));
            writer.putContext(
                "schemas",
                new SchemaGenerator(
                    writer,
                    directive.shape(),
                    directive.symbolProvider(),
                    directive.model(),
                    directive.context()
                )
            );
            writer.putContext("properties", new PropertyGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext(
                "constructor",
                new ConstructorGenerator(writer, shape, directive.symbolProvider(), directive.model())
            );
            writer.putContext(
                "getters",
                new GetterGenerator(writer, shape, directive.symbolProvider(), directive.model())
            );
            writer.putContext("equals", new EqualsGenerator(writer, directive.shape(), directive.symbolProvider()));
            writer.putContext("hashCode", new HashCodeGenerator(writer, directive.shape(), directive.symbolProvider()));
            writer.putContext("toString", writer.consumer(JavaWriter::writeToString));
            writer.putContext(
                "memberSerializer",
                new StructureSerializerGenerator(
                    writer,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.shape(),
                    directive.service()
                )
            );
            writer.putContext(
                "builder",
                new BuilderGenerator(writer, shape, directive.symbolProvider(), directive.model(), directive.service())
            );
            writer.write(
                """
                    public final class ${shape:T} implements ${serializableStruct:T} {
                        ${id:C|}

                        ${schemas:C|}

                        ${properties:C|}

                        ${constructor:C|}

                        ${getters:C|}

                        ${toString:C|}

                        ${equals:C|}

                        ${hashCode:C|}

                        @Override
                        public SdkSchema schema() {
                            return SCHEMA;
                        }

                        ${memberSerializer:C|}

                        ${builder:C|}
                    }
                    """
            );
            writer.popState();
        });
    }
}
