/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.generators.SchemaFieldOrder.SchemaField;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.MixinTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SchemasGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {

        var order = directive.context().schemaFieldOrder();
        for (var shapeOrder : order.partitions()) {
            var className = shapeOrder.get(0).className();
            var fileName = String
                    .format("./%s/model/%s.java", directive.settings().packageNamespace().replace(".", "/"), className);
            directive.context()
                    .writerDelegator()
                    .useFileWriter(fileName, CodegenUtils.getModelNamespace(directive.settings()), writer -> {
                        var template =
                                """
                                        /**
                                         * Defines schemas for shapes in the model package.
                                         */
                                        final class ${className:L} {
                                            ${#builders}${value:C|}
                                            ${/builders}${schemas:C|}
                                            ${#resolvers}
                                            ${value:C|}
                                            ${/resolvers}

                                            private ${className:L}() {}
                                        }
                                        """;
                        var recursiveShapes =
                                shapeOrder.stream()
                                        .filter(SchemaField::isRecursive)
                                        .filter(not(SchemaField::isExternal))
                                        .toList();
                        var builders = recursiveShapes.stream()
                                .map(s -> new SchemaBuilderGenerator(writer,
                                        s,
                                        directive.model(),
                                        directive.context()))
                                .toList();
                        var resolvers = recursiveShapes.stream()
                                .map(s -> new ResolverGenerator(writer, s))
                                .toList();
                        var schemas = new StaticSchemaFieldsGenerator(directive, shapeOrder, writer);
                        writer.pushState();
                        writer.putContext("className", className);
                        writer.putContext("schemas", schemas);
                        writer.putContext("builders", builders);
                        writer.putContext("resolvers", resolvers);
                        writer.write(template);
                        writer.popState();
                    });
        }

    }

    private static final class StaticSchemaFieldsGenerator implements Runnable {
        private final JavaWriter writer;
        private final List<SchemaField> schemaFields;
        private final CodeGenerationContext context;
        private final ContextualDirective<CodeGenerationContext, ?> directive;
        private boolean insideStaticBlock;

        private StaticSchemaFieldsGenerator(
                ContextualDirective<CodeGenerationContext, ?> directive,
                List<SchemaField> schemaFields,
                JavaWriter writer
        ) {
            this.directive = directive;
            this.writer = writer;
            this.schemaFields = schemaFields;
            this.context = directive.context();
            this.insideStaticBlock = false;
        }

        @Override
        public void run() {
            writer.pushState();
            for (SchemaField schemaField : schemaFields) {
                if (schemaField.isExternal()) {
                    continue;
                }
                if (schemaField.isRecursive() && !insideStaticBlock) {
                    insideStaticBlock = true;
                    writer.openBlock("\nstatic {");
                } else if (!schemaField.isRecursive() && insideStaticBlock) {
                    insideStaticBlock = false;
                    writer.closeBlock("}\n");
                }
                writer.pushState();
                writer.putContext("schemaClass", Schema.class);
                writer.putContext("id", CodegenUtils.getOriginalId(schemaField.shape()));
                writer.putContext("shapeId", ShapeId.class);
                writer.putContext("schemaBuilder", SchemaBuilder.class);
                writer.putContext("name", schemaField.fieldName());
                writer.putContext("traits", new TraitInitializerGenerator(writer, schemaField.shape(), context));
                schemaField.shape().accept(new StaticSchemaFieldGenerator(writer, schemaField, directive));
                writer.popState();
            }
            if (insideStaticBlock) {
                writer.closeBlock("}\n");
            }
            writer.popState();
        }

        private static final class StaticSchemaFieldGenerator extends ShapeVisitor.Default<Void> {

            private final JavaWriter writer;
            private final SchemaField schemaField;
            private final ContextualDirective<CodeGenerationContext, ?> directive;
            private final Model model;
            private final CodeGenerationContext context;

            private StaticSchemaFieldGenerator(
                    JavaWriter writer,
                    SchemaField schemaField,
                    ContextualDirective<CodeGenerationContext, ?> directive
            ) {
                this.writer = writer;
                this.schemaField = schemaField;
                this.directive = directive;
                this.model = directive.model();
                this.context = directive.context();
            }

            @Override
            protected Void getDefault(Shape shape) {
                throw new IllegalStateException("Tried to create schema field for invalid shape: " + shape);
            }

            @Override
            public Void blobShape(BlobShape blobShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBlob(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void booleanShape(BooleanShape booleanShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBoolean(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void listShape(ListShape shape) {
                String template;
                if (schemaField.isRecursive()) {
                    template = """
                                ${name:L}_BUILDER
                                    ${C|}
                                    .build();
                            """;
                } else {
                    template =
                            """
                                    static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.listBuilder(${shapeId:T}.from(${id:S})${traits:C})
                                        ${C|}
                                        .build();
                                    """;
                }
                writer.write(template, (Runnable) () -> shape.getMember().accept(this));

                return null;
            }

            @Override
            public Void mapShape(MapShape shape) {
                String template;
                if (schemaField.isRecursive()) {
                    template = """
                                ${name:L}_BUILDER
                                    ${C|}
                                    ${C|}
                                    .build();
                            """;
                } else {
                    template =
                            """
                                    static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.mapBuilder(${shapeId:T}.from(${id:S})${traits:C})
                                        ${C|}
                                        ${C|}
                                        .build();
                                    """;
                }
                writer.write(template,
                        (Runnable) () -> shape.getKey().accept(this),
                        (Runnable) () -> shape.getValue().accept(this));
                return null;
            }

            @Override
            public Void byteShape(ByteShape byteShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createByte(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void shortShape(ShortShape shortShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createShort(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void integerShape(IntegerShape integerShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createInteger(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void longShape(LongShape longShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createLong(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void floatShape(FloatShape floatShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createFloat(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void documentShape(DocumentShape documentShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createDocument(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void doubleShape(DoubleShape doubleShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createDouble(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBigInteger(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBigDecimal(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void structureShape(StructureShape shape) {
                if (MixinTrait.isInterfaceMixin(shape)) {
                    return null;
                }
                generateStructMemberSchemas(shape, "structureBuilder");
                return null;
            }

            @Override
            public Void unionShape(UnionShape shape) {
                generateStructMemberSchemas(shape, "unionBuilder");
                return null;
            }

            private void generateStructMemberSchemas(Shape shape, String builderMethod) {
                String template;
                if (schemaField.isRecursive()) {
                    template = """
                                ${name:L}_BUILDER${?hasMembers}
                                    ${C|}
                                    ${/hasMembers}.builderSupplier(${schemaTypeClass:T}::builder)
                                    .build();
                            """;
                } else {
                    template =
                            """
                                    static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.${builderMethod:L}(${shapeId:T}.from(${id:S})${traits:C})${?hasMembers}
                                             ${C|}
                                             ${/hasMembers}.builderSupplier(${schemaTypeClass:T}::builder)
                                             .build();
                                    """;
                }
                writer.pushState();
                writer.putContext("hasMembers", !shape.members().isEmpty());
                writer.putContext("builderMethod", builderMethod);
                writer.putContext("schemaTypeClass", context.symbolProvider().toSymbol(shape));

                writer.write(
                        template,
                        (Runnable) () -> shape.members().forEach(m -> m.accept(this)));

                writer.popState();
            }

            @Override
            public Void memberShape(MemberShape shape) {
                var target = model.expectShape(shape.getTarget());
                writer.pushState();
                writer.putContext("memberName", shape.getMemberName());
                writer.putContext("schema", directive.context().schemaFieldOrder().getSchemaFieldName(target, writer));
                writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
                writer.putContext("recursive", CodegenUtils.recursiveShape(model, target));
                writer.write(".putMember(${memberName:S}, ${schema:L}${?recursive}_BUILDER${/recursive}${traits:C})");
                writer.popState();
                return null;
            }

            @Override
            public Void timestampShape(TimestampShape timestampShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createTimestamp(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }

            @Override
            public Void stringShape(StringShape stringShape) {
                writer.write(
                        "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createString(${shapeId:T}.from(${id:S})${traits:C});");
                return null;
            }
        }

    }

    private static final class SchemaBuilderGenerator extends ShapeVisitor.Default<Void> implements Runnable {
        private final JavaWriter writer;
        private final Shape shape;
        private final Model model;
        private final CodeGenerationContext context;
        private final SchemaField schemaField;

        SchemaBuilderGenerator(
                JavaWriter writer,
                SchemaField schemaField,
                Model model,
                CodeGenerationContext context
        ) {
            this.writer = writer;
            this.schemaField = schemaField;
            this.shape = schemaField.shape();
            this.model = model;
            this.context = context;
        }

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("schemaClass", Schema.class);
            writer.putContext("id", shape.toShapeId());
            writer.putContext("shapeId", ShapeId.class);
            writer.putContext("schemaBuilder", SchemaBuilder.class);
            writer.putContext("name", schemaField.fieldName() + "_BUILDER");
            writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
            shape.accept(this);
            writer.popState();
        }

        @Override
        protected Void getDefault(Shape shape) {
            throw new IllegalStateException("Tried to create schema builder for invalid shape: " + shape);
        }

        @Override
        public Void listShape(ListShape shape) {
            writer.write(
                    "static final ${schemaBuilder:T} ${name:L} = ${schemaClass:T}.listBuilder(${shapeId:T}.from(${id:S})${traits:C});");
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writer.write(
                    "static final ${schemaBuilder:T} ${name:L} = ${schemaClass:T}.mapBuilder(${shapeId:T}.from(${id:S})${traits:C});");
            return null;
        }

        @Override
        public Void structureShape(StructureShape shape) {
            writer.write(
                    "static final ${schemaBuilder:T} ${name:L} = ${schemaClass:T}.structureBuilder(${shapeId:T}.from(${id:S})${traits:C});");
            return null;
        }

        @Override
        public Void unionShape(UnionShape shape) {
            writer.write(
                    "static final ${schemaBuilder:T} ${name:L} = ${schemaClass:T}.structureBuilder(${shapeId:T}.from(${id:S})${traits:C});");
            return null;
        }
    }

    private record ResolverGenerator(JavaWriter writer, SchemaField schemaField) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("schemaClass", Schema.class);
            writer.putContext("name", schemaField.fieldName());
            writer.putContext("builderName", schemaField.fieldName() + "_BUILDER");
            writer.write("static final ${schemaClass:T} ${name:L} = ${builderName:L}.build().resolve();");
            writer.popState();
        }
    }
}
