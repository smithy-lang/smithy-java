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
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
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
        if (directive.symbol().getProperty(SymbolProperties.EXTERNAL_TYPE).isPresent()) {
            // Skip external types.
            return;
        }
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template = """
                    public sealed interface ${shape:T} extends ${serializableStruct:T} {
                        ${schemas:C|}

                        ${id:C|}

                        <T> T getValue();

                        @Override
                        default ${schemaClass:N} schema() {
                            return $$SCHEMA;
                        }

                        @Override
                        default <T> T getMemberValue(${sdkSchema:N} member) {
                            return ${schemaUtils:N}.validateMemberInSchema($$SCHEMA, member, getValue());
                        }

                        ${valueClasses:C|}

                        ${builder:C|}
                    }
                    """;
            writer.putContext("shape", directive.symbol());
            writer.putContext("serializableStruct", SerializableStruct.class);
            writer.putContext("shapeSerializer", ShapeSerializer.class);
            writer.putContext("schemaClass", Schema.class);
            writer.putContext("object", Object.class);
            writer.putContext("objects", Objects.class);
            writer.putContext("sdkSchema", Schema.class);
            writer.putContext("schemaUtils", SchemaUtils.class);
            writer.putContext("id", new IdStringGenerator(writer, shape, true));
            writer.putContext(
                    "schemas",
                    new SchemaFieldGenerator(
                            directive,
                            writer,
                            shape));
            writer.putContext(
                    "valueClasses",
                    new ValueClassGenerator(
                            directive,
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service()));
            writer.putContext(
                    "builder",
                    new UnionBuilderGenerator(
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service()));
            writer.write(template);
            writer.popState();
        });
    }

    private record ValueClassGenerator(
            ContextualDirective<CodeGenerationContext, ?> directive,
            JavaWriter writer,
            UnionShape shape,
            SymbolProvider symbolProvider,
            Model model,
            ServiceShape service) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext("collections", Collections.class);
            for (var member : shape.members()) {
                writer.pushState();
                writer.injectSection(new ClassSection(member));
                var target = model.expectShape(member.getTarget());
                var isUnit = target.hasTrait(UnitTypeTrait.class);

                // Use memberName as the record field name so the accessor is auto-generated
                // Use :N formatter for the type to add @NonNull annotation when applicable
                var template =
                        """
                                record ${memberName:U}Member(${^unit}${member:N} ${memberName:L}${/unit}) implements ${shape:T} {
                                    private static final ${schemaClass:T} ${memberSchema:L} = $$SCHEMA.member(${memberSchemaName:S});
                                    ${?needsConstructor}
                                    public ${memberName:U}Member {
                                        ${?col}${memberName:L} = ${collections:T}.${wrap:L}(${/col}${^primitive}${objects:T}.requireNonNull(${/primitive}${memberName:L}${^primitive}, "Union value cannot be null")${/primitive}${?col})${/col};
                                    }
                                    ${/needsConstructor}
                                    @Override
                                    public void serializeMembers(${shapeSerializer:N} serializer) {
                                        ${serializeMember:C};
                                    }

                                    @Override
                                    @SuppressWarnings("unchecked")
                                    public ${?hasBoxed}${member:B}${/hasBoxed}${^hasBoxed}${member:N}${/hasBoxed} getValue() {
                                        return ${^unit}${memberName:L}${/unit}${?unit}null${/unit};
                                    }

                                    ${toString:C|}
                                }
                                """;
                var memberSymbol = symbolProvider.toSymbol(member);
                var memberName = symbolProvider.toMemberName(member);
                boolean isPrimitive = memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE);
                boolean isCol = target.isMapShape() || target.isListShape();
                boolean needsConstructor = !isUnit && (isCol || !isPrimitive);
                writer.putContext("hasBoxed", memberSymbol.getProperty(SymbolProperties.BOXED_TYPE).isPresent());
                writer.putContext("member", memberSymbol);
                writer.putContext("memberName", memberName);
                writer.putContext("memberSchemaName", member.getMemberName());
                writer.putContext("memberSchema", CodegenUtils.toMemberSchemaName(memberName));
                writer.putContext("schemaClass", Schema.class);
                writer.putContext("toString", new ToStringGenerator(writer));
                // Use memberName as the field name for serialization
                writer.putContext(
                        "serializeMember",
                        new SerializerMemberGenerator(directive, writer, member, memberName));
                writer.putContext("primitive", isPrimitive);
                writer.putContext(
                        "wrap",
                        memberSymbol.getProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER).orElse(null));
                writer.putContext("unit", isUnit);
                writer.putContext("col", isCol);
                writer.putContext("needsConstructor", needsConstructor);
                writer.write(template);
                writer.popState();
            }
            generateUnknownVariant();
            writer.popState();
        }

        private void generateUnknownVariant() {
            writer.pushState();
            var template =
                    """
                            record $$Unknown(${string:T} memberName) implements ${shape:T} {
                                @Override
                                public void serialize(${shapeSerializer:T} serializer) {
                                    throw new ${unsupportedOperation:T}("Cannot serialize union with unknown member " + this.memberName);
                                }

                                @Override
                                public void serializeMembers(${shapeSerializer:T} serializer) {}

                                @Override
                                @SuppressWarnings("unchecked")
                                public String getValue() {
                                    return memberName;
                                }

                                private record $$Hidden() implements ${shape:T} {
                                    @Override
                                    public void serializeMembers(${shapeSerializer:T} serializer) {}

                                    @Override
                                    @SuppressWarnings("unchecked")
                                    public <T> T getValue() {
                                        return null;
                                    }
                                }
                            }
                            """;
            writer.putContext("unsupportedOperation", UnsupportedOperationException.class);
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
                        return setValue(new $$Unknown(memberName));
                    }
                    """);

            writer.pushState();
            writer.putContext("illegalArgument", IllegalArgumentException.class);
            writer.write("""
                    private BuildStage setValue(${shape:T} value) {
                        if (this.value != null) {
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
        protected boolean inInterface() {
            return true;
        }

        @Override
        protected void generateStages(JavaWriter writer) {
            writer.write("""
                    interface BuildStage {
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

        @Override
        protected String getMemberSchemaName(MemberShape member) {
            return writer.format("${memberName:U}Member") + "." + super.getMemberSchemaName(member);

        }
    }
}
