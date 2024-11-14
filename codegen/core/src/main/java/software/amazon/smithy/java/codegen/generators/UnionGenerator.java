/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collections;
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
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaUtils;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.UnitTypeTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class UnionGenerator
    implements Consumer<GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template = """
                public abstract class ${shape:T} implements ${serializableStruct:T} {
                    ${id:C|}

                    ${schemas:C|}

                    private final ${type:N} type;

                    private ${shape:T}(${type:T} type) {
                        this.type = type;
                    }

                    public ${type:N} type() {
                        return type;
                    }

                    ${memberEnum:C|}

                    ${toString:C|}

                    @Override
                    public ${schemaClass:N} schema() {
                        return $$SCHEMA;
                    }

                    @Override
                    public Object getMemberValue(${sdkSchema:N} member) {
                        return ${schemaUtils:N}.validateMemberInSchema($$SCHEMA, member, getValue());
                    }

                    public abstract <T> T getValue();

                    ${valueClasses:C|}

                    @Override
                    public int hashCode() {
                        return ${objects:T}.hash(type, getValue());
                    }

                    @Override
                    public boolean equals(${object:T} other) {
                        if (other == this) {
                            return true;
                        }
                        if (other == null || getClass() != other.getClass()) {
                            return false;
                        }
                        return ${objects:T}.equals(getValue(), ((${shape:T}) other).getValue());
                    }

                    ${builder:C|}
                }
                """;
            writer.putContext("shape", directive.symbol());
            writer.putContext("type", CodegenUtils.getInnerTypeEnumSymbol(directive.symbol()));
            writer.putContext("serializableStruct", SerializableStruct.class);
            writer.putContext("shapeSerializer", ShapeSerializer.class);
            writer.putContext("schemaClass", Schema.class);
            writer.putContext("object", Object.class);
            writer.putContext("objects", Objects.class);
            writer.putContext("document", Document.class);
            writer.putContext("sdkSchema", Schema.class);
            writer.putContext("schemaUtils", SchemaUtils.class);
            writer.putContext("id", new IdStringGenerator(writer, shape));
            writer.putContext(
                "schemas",
                new SchemaGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.context()
                )
            );
            writer.putContext("memberEnum", new TypeEnumGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext("toString", new ToStringGenerator(writer));
            writer.putContext(
                "valueClasses",
                new ValueClassGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service()
                )
            );
            writer.putContext(
                "builder",
                new UnionBuilderGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service()
                )
            );
            writer.write(template);
            writer.popState();
        });
    }

    private record ValueClassGenerator(
        JavaWriter writer, UnionShape shape, SymbolProvider symbolProvider, Model model, ServiceShape service
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext("collections", Collections.class);
            for (var member : shape.members()) {
                writer.pushState();
                writer.injectSection(new ClassSection(member));
                var template = """
                    public static final class ${memberName:U}Member extends ${shape:T} {
                        ${^unit}private final transient ${member:T} value;${/unit}

                        public ${memberName:U}Member(${^unit}${member:N} value${/unit}) {
                            super(Type.${memberName:L});${^unit}
                            this.value = ${?col}${collections:T}.${wrap:L}(${/col}${^primitive}${objects:T}.requireNonNull(${/primitive}value${^primitive}, "Union value cannot be null")${/primitive}${?col})${/col};${/unit}
                        }

                        @Override
                        public void serializeMembers(${shapeSerializer:N} serializer) {
                            ${serializeMember:C};
                        }${^unit}

                        public ${member:N} ${memberName:L}() {
                            return value;
                        }${/unit}

                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> T getValue() {
                            return (T)${?hasBoxed}(${member:B})${/hasBoxed} ${^unit}value${/unit}${?unit}null${/unit};
                        }
                    }
                    """;
                var memberSymbol = symbolProvider.toSymbol(member);
                writer.putContext("hasBoxed", memberSymbol.getProperty(SymbolProperties.BOXED_TYPE).isPresent());
                writer.putContext("member", memberSymbol);
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                writer.putContext(
                    "serializeMember",
                    new SerializerMemberGenerator(writer, symbolProvider, model, service, member, "value")
                );
                writer.putContext("primitive", memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE));
                writer.putContext(
                    "wrap",
                    memberSymbol.getProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER).orElse(null)
                );
                var target = model.expectShape(member.getTarget());
                writer.putContext("unit", target.hasTrait(UnitTypeTrait.class));
                writer.putContext("col", target.isMapShape() || target.isListShape());
                writer.write(template);
                writer.popState();
            }
            generateUnknownVariant();
            writer.popState();
        }

        private void generateUnknownVariant() {
            writer.pushState();
            var template = """
                public static final class $$UnknownMember extends ${shape:T} {
                    private final ${string:T} memberName;

                    public $$UnknownMember(${string:T} memberName) {
                        super(Type.$$UNKNOWN);
                        this.memberName = memberName;
                    }

                    public ${string:T} memberName() {
                        return memberName;
                    }

                    @Override
                    public void serialize(${shapeSerializer:T} serializer) {
                        throw new ${serdeException:T}("Cannot serialize union with unknown member " + this.memberName);
                    }

                    @Override
                    public void serializeMembers(${shapeSerializer:T} serializer) {}

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T getValue() {
                        return (T) memberName;
                    }
                }
                """;
            writer.putContext("serdeException", SerializationException.class);
            writer.putContext("string", String.class);
            writer.write(template);
            writer.popState();
        }
    }

    private static final class UnionBuilderGenerator extends BuilderGenerator {

        UnionBuilderGenerator(
            JavaWriter writer,
            Shape shape,
            SymbolProvider symbolProvider,
            Model model,
            ServiceShape service
        ) {
            super(writer, shape, symbolProvider, model, service);
        }

        @Override
        protected void generateProperties(JavaWriter writer) {
            writer.write("private ${shape:T} value;");
        }

        @Override
        protected void generateSetters(JavaWriter writer) {
            for (var member : shape.members()) {
                writer.pushState();
                writer.putContext("memberName", symbolProvider.toMemberName(member));
                writer.putContext("member", symbolProvider.toSymbol(member));
                var target = model.expectShape(member.getTarget());
                writer.putContext("unit", target.hasTrait(UnitTypeTrait.class));
                writer.write("""
                    public BuildStage ${memberName:L}(${member:T} value) {
                        return setValue(new ${memberName:U}Member(${^unit}value${/unit}));
                    }
                    """);
                writer.popState();
            }

            writer.write("""
                public BuildStage $$unknownMember(String memberName) {
                    return setValue(new $$UnknownMember(memberName));
                }
                """);

            writer.pushState();
            writer.putContext("illegalArgument", IllegalArgumentException.class);
            writer.write("""
                private BuildStage setValue(${shape:T} value) {
                    if (this.value != null) {
                        if (this.value.type() == Type.$$UNKNOWN) {
                            throw new ${illegalArgument:T}("Cannot change union from unknown to known variant");
                        }
                        throw new ${illegalArgument:T}("Only one value may be set for unions");
                    }
                    this.value = value;
                    return this;
                }
                """);

            writer.popState();
        }

        @Override
        protected List<String> stageInterfaces() {
            return List.of("BuildStage");
        }

        @Override
        protected void generateStages(JavaWriter writer) {
            writer.write("""
                public interface BuildStage {
                    ${shape:T} build();
                }
                """);
        }

        @Override
        protected void generateBuild(JavaWriter writer) {
            writer.write("""
                @Override
                public ${shape:N} build() {
                    return ${objects:T}.requireNonNull(value, "no union value set");
                }
                """);
        }
    }
}
