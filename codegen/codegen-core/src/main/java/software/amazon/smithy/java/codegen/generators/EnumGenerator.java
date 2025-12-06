/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.sections.EnumVariantSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class EnumGenerator<T extends ShapeDirective<Shape, CodeGenerationContext, JavaCodegenSettings>>
        implements Consumer<T> {

    @Override
    public void accept(T directive) {
        if (directive.symbol().getProperty(SymbolProperties.EXTERNAL_TYPE).isPresent()) {
            // skip external types
            return;
        }
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template = """
                    public sealed interface ${shape:T} extends ${enum:T}, ${serializableShape:T} {
                        ${staticImpls:C|}

                        ${schema:C|}

                        ${id:C|}

                        ${value:N} getValue();

                        @Override
                        default void serialize(${shapeSerializer:T} serializer) {
                            serializer.${writeMethod:L}($$SCHEMA, getValue());
                        }

                        ${staticMethods:C|}

                        ${variantClasses:C|}

                        ${builder:C|}
                    }
                    """;
            var shapeSymbol = directive.symbolProvider().toSymbol(shape);
            writer.putContext("shape", shapeSymbol);
            writer.putContext("serializableShape", SerializableShape.class);
            writer.putContext("enum", shape.isEnumShape() ? SmithyEnum.class : SmithyIntEnum.class);
            writer.putContext("shapeSerializer", ShapeSerializer.class);
            var valueSymbol = shapeSymbol.expectProperty(SymbolProperties.ENUM_VALUE_TYPE);
            writer.putContext("value", valueSymbol);
            writer.putContext("string", shape.isEnumShape());
            writer.putContext("writeMethod", shape.isEnumShape() ? "writeString" : "writeInteger");

            writer.putContext("id", new IdStringGenerator(writer, shape, true));
            writer.putContext("staticImpls", new StaticImplGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext(
                    "schema",
                    new SchemaFieldGenerator(directive,
                            writer,
                            shape));
            writer.putContext("staticMethods", new StaticMethodsGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext(
                    "variantClasses",
                    new VariantClassGenerator(
                            writer,
                            shape,
                            directive.symbolProvider()));
            writer.putContext(
                    "builder",
                    new EnumBuilderGenerator(
                            writer,
                            shape,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service()));
            writer.write(template);
            writer.popState();
        });
    }

    private record StaticImplGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements
            Runnable {
        @Override
        public void run() {
            writer.pushState();
            List<String> types = new ArrayList<>();
            for (var member : shape.members()) {
                writer.pushState(new EnumVariantSection(member));
                var fieldName = symbolProvider.toMemberName(member);
                var className = toClassName(fieldName);
                types.add(fieldName);
                writer.putContext("fieldName", fieldName);
                writer.putContext("className", className);
                writer.write("${shape:T} ${fieldName:L} = new ${className:L}();");
                writer.popState();
            }
            writer.putContext("types", types);
            writer.putContext("list", List.class);
            writer.write(
                    "${list:T}<${shape:T}> $$TYPES = ${list:T}.of(${#types}${value:L}${^key.last}, ${/key.last}${/types});");
            writer.popState();
        }
    }

    private record StaticMethodsGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements
            Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("list", List.class);
            writer.putContext("illegalArg", IllegalArgumentException.class);
            var template = """
                    /**
                     * Create an unknown enum variant with the given value.
                     *
                     * @param value value for the unknown variant.
                     */
                    static ${shape:T} unknown(${value:T} value) {
                        return new $$Unknown(value);
                    }

                    /**
                     * Returns an unmodifiable list containing the constants of this enum type, in the order declared.
                     */
                    static ${list:T}<${shape:T}> values() {
                        return $$TYPES;
                    }

                    /**
                     * Returns a {@link ${shape:T}} constant with the specified value.
                     *
                     * @param value value to create {@code ${shape:T}} from.
                     * @throws ${illegalArg:T} if value does not match a known value.
                     */
                    static ${shape:T} from(${value:T} value) {
                        return switch (value) {
                            ${fromCases:C|}
                            default -> throw new ${illegalArg:T}("Unknown value: " + value);
                        };
                    }
                    """;
            writer.putContext("fromCases", writer.consumer(w -> generateSwitchCases(w, shape, symbolProvider)));
            writer.write(template);
            writer.popState();
        }
    }

    private record VariantClassGenerator(
            JavaWriter writer,
            Shape shape,
            SymbolProvider symbolProvider) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            var enumValues = getEnumValues(shape);

            // Generate known enum variant classes
            for (var member : shape.members()) {
                writer.pushState();
                var fieldName = symbolProvider.toMemberName(member);
                var className = toClassName(fieldName);
                var memberValue = enumValues.get(member.getMemberName());

                var template = """
                        final class ${className:L} implements ${shape:T} {
                            private ${className:L}() {}

                            @Override
                            public ${value:N} getValue() {
                                return ${?string}${memberValue:S}${/string}${^string}${memberValue:L}${/string};
                            }

                            ${toString:C|}
                        }
                        """;
                writer.putContext("className", className);
                writer.putContext("memberValue", memberValue);
                writer.putContext("toString", new ToStringGenerator(writer));
                writer.write(template);
                writer.popState();
            }

            // Generate $Unknown variant
            generateUnknownVariant();
            writer.popState();
        }

        private void generateUnknownVariant() {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext("toString", new ToStringGenerator(writer));
            var template = """
                    record $$Unknown(${value:N} value) implements ${shape:T} {${?string}
                        public $$Unknown {
                            ${objects:T}.requireNonNull(value, "Value cannot be null");
                        }${/string}

                        @Override
                        public ${value:N} getValue() {
                            return value;
                        }

                        ${toString:C|}

                        private final class $$Hidden implements ${shape:T} {
                            @Override
                            public ${value:N} getValue() {
                                return ${?string}null${/string}${^string}0${/string};
                            }
                        }
                    }
                    """;
            writer.write(template);
            writer.popState();
        }
    }

    private static final class EnumBuilderGenerator extends BuilderGenerator {

        EnumBuilderGenerator(
                JavaWriter writer,
                Shape shape,
                SymbolProvider symbolProvider,
                Model model,
                ServiceShape service
        ) {
            super(writer, shape, symbolProvider, model, service);
        }

        @Override
        protected boolean inInterface() {
            return true;
        }

        @Override
        protected void generateDeserialization(JavaWriter writer) {
            writer.pushState();
            writer.putContext("string", shape.isEnumShape());
            writer.putContext("shapeDeserializer", ShapeDeserializer.class);
            writer.write(
                    """
                            @Override
                            public Builder deserialize(${shapeDeserializer:N} de) {
                                return value(de.${?string}readString${/string}${^string}readInteger${/string}($$SCHEMA));
                            }""");
            writer.popState();
        }

        @Override
        protected void generateProperties(JavaWriter writer) {
            writer.write("private ${value:T} value;");
        }

        @Override
        protected void generateSetters(JavaWriter writer) {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext(
                    "primitive",
                    symbolProvider.toSymbol(shape)
                            .expectProperty(SymbolProperties.ENUM_VALUE_TYPE)
                            .expectProperty(SymbolProperties.IS_PRIMITIVE));
            writer.write(
                    """
                            private Builder value(${value:T} value) {
                                this.value = ${^primitive}${objects:T}.requireNonNull(${/primitive}value${^primitive}, "Enum value cannot be null")${/primitive};
                                return this;
                            }
                            """);
            writer.popState();
        }

        @Override
        protected void generateBuild(JavaWriter writer) {
            writer.write("""
                    @Override
                    public ${shape:T} build() {
                        return switch (value) {
                            ${C|}
                            default -> new $$Unknown(value);
                        };
                    }
                    """, writer.consumer(w -> generateSwitchCases(w, shape, symbolProvider)));
        }
    }

    private static void generateSwitchCases(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) {
        writer.pushState();
        writer.putContext("string", shape.isEnumShape());
        var enumValue = getEnumValues(shape);
        for (var member : shape.members()) {
            writer.write(
                    "case ${?string}$1S${/string}${^string}$1L${/string} -> $2L;",
                    enumValue.get(member.getMemberName()),
                    symbolProvider.toMemberName(member));
        }
        writer.popState();
    }

    private static Map<String, String> getEnumValues(Shape shape) {
        if (shape instanceof EnumShape se) {
            return se.getEnumValues();
        } else if (shape instanceof IntEnumShape ie) {
            return ie.getEnumValues()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        } else {
            throw new IllegalArgumentException("Expected Int enum or enum");
        }
    }

    /**
     * Converts an enum field name (typically UPPER_SNAKE_CASE like OPTION_ONE)
     * to a class name (PascalCase like OptionOneType). The "Type" suffix avoids
     * conflicts with java.lang types like String, Boolean, etc.
     */
    private static String toClassName(String fieldName) {
        return CaseUtils.toPascalCase(fieldName) + "Type";
    }
}
