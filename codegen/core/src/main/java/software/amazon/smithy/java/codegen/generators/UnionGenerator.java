/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.UnionShape;

public class UnionGenerator
    implements Consumer<GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            writer.putContext("shape", directive.symbol());
            writer.putContext("serializableStruct", SerializableStruct.class);
            writer.putContext("document", Document.class);
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
            writer.putContext("memberEnum", new MemberEnumGenerator(writer, directive.symbolProvider(), shape));
            writer.putContext("schemaGetter", writer.consumer(JavaWriter::writeSchemaGetter));
            writer.putContext("toString", writer.consumer(JavaWriter::writeToString));
            writer.putContext("valueCasters", new ValueCasterGenerator(writer, directive.symbolProvider(), shape));
            writer.putContext(
                "valueClasses",
                new ValueClassGenerator(
                    writer,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service(),
                    shape
                )
            );
            writer.putContext("builderGetter", writer.consumer(JavaWriter::writeBuilderGetter));
            writer.putContext(
                "builder",
                new BuilderGenerator(writer, directive.symbolProvider(), directive.model(), directive.service(), shape)
            );

            // Register inner templates for builder generator
            writer.putContext("builderProperties", writer.consumer(w -> w.write("private ${shape:T} value;")));
            writer.putContext("builderSetters", new BuilderSetterGenerator(writer, directive.symbolProvider(), shape));
            writer.putContext("errorCorrection", (Runnable) () -> {});
            writer.putContext("buildMethod", writer.consumer(UnionGenerator::generateBuildMethod));
            writer.write("""
                public abstract class ${shape:T} implements ${serializableStruct:T} {
                    ${id:C|}

                    ${schemas:C|}

                    private final Member type;

                    private ${shape:T}(Member type) {
                        this.type = type;
                    }

                    public Member type() {
                        return type;
                    }

                    ${memberEnum:C|}

                    ${schemaGetter:C|}

                    ${toString:C|}

                    ${valueCasters:C|}

                    ${valueClasses:C|}

                    ${builderGetter:C|}

                    ${builder:C|}
                }
                """);
            writer.popState();
        });
    }

    private record MemberEnumGenerator(JavaWriter writer, SymbolProvider symbolProvider, UnionShape shape) implements
        Runnable {
        @Override
        public void run() {
            List<String> enumList = new ArrayList<>();
            enumList.add("$$UNKNOWN");
            shape.members()
                .stream()
                .map(member -> CodegenUtils.getEnumVariantName(symbolProvider, member))
                .forEach(enumList::add);
            writer.pushState();
            writer.putContext("variants", enumList);
            writer.write("""
                public enum Member {
                    ${#variants}${value:L}${^key.last},
                    ${/key.last}${/variants}
                }
                """);
            writer.popState();
        }
    }

    private record ValueCasterGenerator(JavaWriter writer, SymbolProvider symbolProvider, UnionShape shape) implements
        Runnable {
        @Override
        public void run() {
            writer.pushState();
            for (var member : shape.members()) {
                writer.pushState();
                writer.putContext("member", symbolProvider.toSymbol(member));
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                writer.write("""
                    public ${member:B} ${memberName:L}() {
                        return null;
                    }
                    """);
                writer.popState();
            }
            writer.write("""
                public ${document:T} $$$$unknown() {
                    return null;
                }
                """);
            writer.popState();
        }
    }

    private record ValueClassGenerator(
        JavaWriter writer, SymbolProvider symbolProvider, Model model, ServiceShape service, UnionShape shape
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("shapeSerializer", ShapeSerializer.class);
            writer.putContext("objects", Objects.class);
            for (var member : shape.members()) {
                writer.pushState();
                // TODO: ensure these variants have docs added?
                var memberSymbol = symbolProvider.toSymbol(member);
                writer.putContext("member", memberSymbol);
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                writer.putContext("enumValue", CodegenUtils.getEnumVariantName(symbolProvider, member));
                writer.putContext(
                    "serializeMember",
                    new SerializerMemberGenerator(writer, symbolProvider, model, service, member, "value")
                );
                writer.putContext("equals", new EqualsGenerator(writer, symbolProvider, member));
                writer.putContext("hashCode", new HashCodeGenerator(writer, symbolProvider, member));
                writer.putContext("primitive", memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE));
                writer.write(
                    """
                        public static final class ${memberName:U}Member extends ${shape:T} {
                            private final transient ${member:T} value;

                            public ${memberName:U}Member(${member:T} value) {
                                super(Member.${enumValue:L});
                                this.value = ${^primitive}${objects:T}.requireNonNull(${/primitive}value${^primitive}, "Union value cannot be null")${/primitive};
                            }

                            @Override
                            public void serializeMembers(${shapeSerializer:T} serializer) {
                                ${serializeMember:C};
                            }

                            @Override
                            public ${member:B} ${memberName:L}() {
                                return value;
                            }

                            ${equals:C|}

                            ${hashCode:C|}
                        }
                        """
                );
                writer.popState();
            }
            writer.write("""
                public static final class $$$$Unknown extends ${shape:T} {
                    private final transient ${document:T} value;

                    $$$$Unknown (${document:T} value) {
                        super(Member.$$$$UNKNOWN);
                        this.value = value;
                    }

                    @Override
                    public void serializeMembers(${shapeSerializer:T} serializer) {
                        serializer.writeDocument(value);
                    }

                    @Override
                    public ${document:T} $$$$unknown() {
                        return value;
                    }
                }
                """);
            writer.popState();
        }
    }

    private static void generateBuildMethod(JavaWriter writer) {
        writer.pushState();
        writer.putContext("objects", Objects.class);
        writer.write("""
            @Override
            public ${shape:T} build() {
                return ${objects:T}.requireNonNull(value, "no union value set");
            }
            """);
        writer.popState();
    }

    private record BuilderSetterGenerator(JavaWriter writer, SymbolProvider provider, UnionShape shape) implements
        Runnable {

        @Override
        public void run() {
            for (var member : shape.members()) {
                writer.pushState();
                writer.putContext("memberName", provider.toMemberName(member));
                writer.putContext("member", provider.toSymbol(member));
                writer.write("""
                    public Builder ${memberName:L}(${member:T} value) {
                        checkForExistingValue();
                        this.value = new ${memberName:U}Member(value);
                        return this;
                    }
                    """);
                writer.popState();
            }

            writer.pushState();
            writer.putContext("serdeException", SdkSerdeException.class);
            writer.write("""
                private void checkForExistingValue() {
                    if (this.value != null) {
                        throw new ${serdeException:T}("Only one value may be set for unions");
                    }
                }

                private void $$$$unknown(${document:T} value) {
                    checkForExistingValue();
                    this.value = new $$$$Unknown(value);
                }
                """);
            writer.popState();
        }
    }

    private record HashCodeGenerator(JavaWriter writer, SymbolProvider symbolProvider, MemberShape shape) implements
        Runnable {
        @Override
        public void run() {
            writer.write(
                """
                    @Override
                    public int hashCode() {
                        ${C|}
                    }""",
                (Runnable) this::generate
            );
        }

        private void generate() {
            writer.pushState();
            if (CodegenUtils.isJavaArray(symbolProvider.toSymbol(shape))) {
                writer.putContext("arrays", Arrays.class);
                writer.write("return ${arrays:T}.hashCode(value);");
            } else {
                writer.putContext("objects", Objects.class);
                writer.write("return ${objects:T}.hash(value);");
            }
            writer.popState();
        }
    }

    private record EqualsGenerator(JavaWriter writer, SymbolProvider symbolProvider, MemberShape shape) implements
        Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(shape));
            writer.putContext("object", Object.class);
            writer.write(
                """
                    @Override
                    public boolean equals(${object:T} other) {
                        if (other == this) {
                            return true;
                        }
                        if (other == null || getClass() != other.getClass()) {
                            return false;
                        }
                        ${memberName:U}Member that = (${memberName:U}Member) other;
                        return ${C};
                    }""",
                (Runnable) this::writePropertyEqualityCheck
            );
            writer.popState();
        }

        private void writePropertyEqualityCheck() {
            var memberSymbol = symbolProvider.toSymbol(shape);
            if (memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE)) {
                writer.writeInlineWithNoFormatting("value == that.value");
            } else {
                Class<?> comparator = CodegenUtils.isJavaArray(memberSymbol) ? Arrays.class : Objects.class;
                writer.writeInline("$T.equals(value, that.value)", comparator);
            }
        }
    }
}
