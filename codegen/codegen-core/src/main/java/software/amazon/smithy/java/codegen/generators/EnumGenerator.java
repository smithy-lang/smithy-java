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
import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.UnknownEnumValue;
import software.amazon.smithy.java.core.schema.UnknownIntEnumValue;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class EnumGenerator<T extends ShapeDirective<Shape, CodeGenerationContext, JavaCodegenSettings>>
        implements Consumer<T> {

    @Override
    public void accept(T directive) {
        if (directive.symbol().getProperty(SymbolProperties.EXTERNAL_TYPE).orElse(false)) {
            // skip external types
            return;
        }
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template =
                    """
                            public sealed interface ${shape:T} extends ${enum:T}, ${serializableShape:T} permits ${#variants}${shape:T}.${value:L}, ${/variants}${shape:T}.$$Unknown, ${hidden:L} {
                                ${staticImpls:C|}

                                ${schema:C|}

                                ${id:C|}

                                ${value:T} getValue();

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
            List<String> variants = new ArrayList<>();
            for (var member : shape.members()) {
                variants.add(directive.symbolProvider()
                        .toSymbol(member)
                        .expectProperty(SymbolProperties.ENUM_VARIANT_CLASS_NAME));
            }
            writer.putContext("variants", variants);
            writer.putContext(
                    "hidden",
                    EnumHiddenVariantGenerator.classNameFor(directive, directive.symbolProvider(), shape));

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
                            directive.getRenames(),
                            directive.context().schemaFieldOrder()));
            writer.writeNullMarkedAnnotation();
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
                types.add(fieldName);
                writer.putContext("fieldName", fieldName);
                writer.putContext("className",
                        symbolProvider.toSymbol(member).expectProperty(SymbolProperties.ENUM_VARIANT_CLASS_NAME));
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
            var template =
                    """
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
                             * <p>Use {@link #fromValue} when reading values from a service, since a newer service may
                             * return values that this client does not know.
                             *
                             * @param value value to create {@code ${shape:T}} from.
                             * @throws ${illegalArg:T} if value does not match a known value.
                             */
                            static ${shape:T} from(${value:T} value) {
                                var result = $$lookup(value);
                                if (result == null) {
                                    throw new ${illegalArg:T}("Unknown value: ".concat(${?string}value${/string}${^string}${integer:T}.toString(value)${/string}));
                                }
                                return result;
                            }

                            /**
                             * Returns the {@link ${shape:T}} constant with the specified value, or an unknown variant
                             * if no constant matches.
                             *
                             * <p>This method never throws for unrecognized values. Use it when reading values from a
                             * service, since a newer service may return values that this client does not know.
                             * Use {@link #from} when the value must be one of the known constants.
                             *
                             * @param value value to create {@code ${shape:T}} from.
                             */
                            static ${shape:T} fromValue(${value:T} value) {
                                var result = $$lookup(value);
                                return result != null ? result : new $$Unknown(value);
                            }

                            private static ${shape:T} $$lookup(${value:T} value) {
                                return switch (value) {
                                    ${fromCases:C|}
                                    default -> null;
                                };
                            }
                            """;
            writer.putContext("integer", Integer.class);
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

            boolean string = shape.isEnumShape();
            writer.putContext("knownBase", string ? EnumValue.class : IntEnumValue.class);
            writer.putContext("unknownBase", string ? UnknownEnumValue.class : UnknownIntEnumValue.class);
            var enumValues = getEnumValues(shape);
            for (var member : shape.members()) {
                writer.pushState();
                writer.putContext("className",
                        symbolProvider.toSymbol(member).expectProperty(SymbolProperties.ENUM_VARIANT_CLASS_NAME));
                writer.putContext("memberValue", enumValues.get(member.getMemberName()));
                writer.write("""
                        final class ${className:L} extends ${knownBase:T} implements ${shape:T} {
                            private ${className:L}() {
                                super(${?string}${memberValue:S}${/string}${^string}${memberValue:L}${/string});
                            }
                        }
                        """);
                writer.popState();
            }

            // Generate $Unknown variant
            generateUnknownVariant();
            writer.popState();
        }

        private void generateUnknownVariant() {
            writer.write("""
                    final class $$Unknown extends ${unknownBase:T} implements ${shape:T} {
                        public $$Unknown(${value:T} value) {
                            super(value);
                        }
                    }
                    """);
        }
    }

    private static final class EnumBuilderGenerator extends BuilderGenerator {

        EnumBuilderGenerator(
                JavaWriter writer,
                Shape shape,
                SymbolProvider symbolProvider,
                Model model,
                Map<ShapeId, String> renames,
                SchemaFieldOrder schemaFieldOrder
        ) {
            super(writer, shape, symbolProvider, model, renames, schemaFieldOrder);
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
                            public Builder deserialize(${shapeDeserializer:T} de) {
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
                        return fromValue(value);
                    }
                    """);
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
}
