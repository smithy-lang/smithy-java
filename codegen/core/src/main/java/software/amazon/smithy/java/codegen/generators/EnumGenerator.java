/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

// TODO: handle strings with enum trait
// TODO: Equals and hashcode
@SmithyInternalApi
public class EnumGenerator<T extends ShapeDirective<Shape, CodeGenerationContext, JavaCodegenSettings>>
    implements Consumer<T> {
    @Override
    public void accept(T directive) {
        var shape = directive.shape();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var shapeSymbol = directive.symbolProvider().toSymbol(shape);
            writer.putContext("shape", shapeSymbol);
            writer.putContext("serializableShape", SerializableShape.class);
            var valueSymbol = shapeSymbol.expectProperty(SymbolProperties.ENUM_VALUE_TYPE);
            writer.putContext("value", valueSymbol);

            writer.putContext("id", new IdStringGenerator(writer, shape));
            writer.putContext("staticImpls", new StaticImplGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext(
                "schema",
                new SchemaGenerator(writer, shape, directive.symbolProvider(), directive.model(), directive.context())
            );
            writer.putContext("properties", writer.consumer(EnumGenerator::writeProperties));
            writer.putContext("constructor", new ConstructorGenerator(writer, valueSymbol));
            writer.putContext("innerEnum", new TypeEnumGenerator(writer, shape, directive.symbolProvider()));
            writer.putContext("getters", writer.consumer(EnumGenerator::writeGetters));
            writer.putContext(
                "serializer",
                new SerializerGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service()
                )
            );
            writer.putContext("toString", new ToStringGenerator(writer));
            writer.putContext("equals", new EqualsGenerator(writer, valueSymbol));
            writer.putContext("hashCode", new HashCodeGenerator(writer, valueSymbol));
            writer.putContext(
                "builder",
                new EnumBuilderGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service()
                )
            );

            writer.write(
                """
                    public final class ${shape:T} implements ${serializableShape:T} {
                        ${id:C|}
                        ${staticImpls:C|}

                        ${schema:C|}

                        ${properties:C|}

                        ${constructor:C|}

                        ${innerEnum:C|}

                        ${getters:C|}

                        ${serializer:C|}

                        ${toString:C|}

                        ${equals:C|}

                        ${hashCode:C|}

                        ${builder:C|}
                    }
                    """
            );
            writer.popState();
        });
    }

    private record StaticImplGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements
        Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("string", shape.isEnumShape());
            for (var entry : getEnumValues(shape).entrySet()) {
                writer.pushState();
                writer.putContext("var", CodegenUtils.toUpperSnakeCase(entry.getKey()));
                writer.putContext("val", entry.getValue());
                writer.write(
                    "public static final ${shape:T} ${var:L} = new ${shape:T}(Type.${var:L}, ${?string}${val:S}${/string}${^string}${val:L}${/string});"
                );
                writer.popState();
            }
            writer.popState();
        }
    }

    private static void writeProperties(JavaWriter writer) {
        writer.write("""
            private final ${value:T} value;
            private final Type type;
            """);
    }

    private record ConstructorGenerator(JavaWriter writer, Symbol value) implements
        Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("objects", Objects.class);
            writer.putContext("primitive", value.expectProperty(SymbolProperties.IS_PRIMITIVE));
            writer.write(
                """
                    private ${shape:T}(Type type, ${value:T} value) {
                        this.type = ${objects:T}.requireNonNull(type, "type cannot be null");
                        this.value = ${^primitive}${objects:T}.requireNonNull(${/primitive}value${^primitive}, "value cannot be null")${/primitive};
                    }
                    """
            );
            writer.popState();
        }
    }

    private static void writeGetters(JavaWriter writer) {
        writer.write("""
            public ${value:T} value() {
                return value;
            }

            public Type type() {
                return type;
            }

            public static ${shape:T} unknown(${value:T} value) {
                return new ${shape:T}(Type.$$$$UNKNOWN, value);
            }
            """);
    }

    private record SerializerGenerator(
        JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model, ServiceShape service
    ) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("shapeSerializer", ShapeSerializer.class);
            writer.putContext(
                "serializerBody",
                new SerializerMemberGenerator(writer, symbolProvider, model, service, shape, "this")
            );
            writer.write("""
                @Override
                public void serialize(${shapeSerializer:T} serializer) {
                    ${serializerBody:C|};
                }
                """);
            writer.popState();
        }
    }

    private record EqualsGenerator(JavaWriter writer, Symbol value) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("object", Object.class);
            writer.putContext("primitive", value.expectProperty(SymbolProperties.IS_PRIMITIVE));
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
                        ${shape:T} that = (${shape:T}) other;
                        return this.value${?primitive} == ${/primitive}${^primitive}.equals(${/primitive}that.value${^primitive})${/primitive};
                    }"""
            );
            writer.popState();
        }
    }

    private record HashCodeGenerator(JavaWriter writer, Symbol value) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("primitive", value.expectProperty(SymbolProperties.IS_PRIMITIVE));
            writer.write("""
                @Override
                public int hashCode() {
                    return ${?primitive}value${/primitive}${^primitive}value.hashCode()${/primitive};
                }
                """);
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
        protected void generateDeserialization(JavaWriter writer) {
            writer.pushState();
            writer.putContext("string", shape.isEnumShape());
            writer.putContext("shapeDeserializer", ShapeDeserializer.class);
            writer.write(
                """
                    @Override
                    public Builder deserialize(${shapeDeserializer:T} de) {
                        return value(de.${?string}readString${/string}${^string}readInteger${/string}(SCHEMA));
                    }
                    """
            );
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
                    .expectProperty(SymbolProperties.IS_PRIMITIVE)
            );
            writer.write(
                """
                    private Builder value(${value:T} value) {
                        this.value = ${^primitive}${objects:T}.requireNonNull(${/primitive}value${^primitive}, "Enum value cannot be null")${/primitive};
                        return this;
                    }
                    """
            );
            writer.popState();
        }

        @Override
        protected void generateBuild(JavaWriter writer) {
            writer.write("""
                @Override
                public ${shape:T} build() {
                    return switch (value) {
                        ${C|}
                        default -> new ${shape:T}(Type.$$$$UNKNOWN, value);
                    };
                }
                """, writer.consumer(this::generateSwitchCases));
        }

        private void generateSwitchCases(JavaWriter writer) {
            writer.pushState();
            writer.putContext("string", shape.isEnumShape());
            for (var entry : getEnumValues(shape).entrySet()) {
                writer.write(
                    "case ${?string}$1S${/string}${^string}$1L${/string} -> $2L;",
                    entry.getValue(),
                    CodegenUtils.toUpperSnakeCase(entry.getKey())
                );
            }
            writer.popState();
        }
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
