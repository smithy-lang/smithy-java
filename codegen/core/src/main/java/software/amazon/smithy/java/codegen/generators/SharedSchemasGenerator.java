/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
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
                    var shapesToGenerate = getCommonShapes(directive.connectedShapes().values());
                    writer.write(
                        """
                            /**
                             * Defines shared shapes across the model package that are not part of another code-generated type.
                             */
                            final class SharedSchemas {

                                ${C|}

                                ${C|}

                                private SharedSchemas() {}
                            }
                            """,
                        writer.consumer(w -> this.generateSchemas(w, directive, shapesToGenerate)),
                        writer.consumer(w -> this.generateSerdeMethods(w, directive, shapesToGenerate))
                    );
                }
            );
    }

    private void generateSchemas(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Set<Shape> shapes
    ) {
        for (var shape : shapes) {
            new SchemaGenerator(writer, shape, directive.symbolProvider(), directive.model(), directive.context())
                .run();
        }
    }

    private void generateSerdeMethods(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Set<Shape> shapes
    ) {
        for (var shape : shapes) {
            // Only Map and List shapes need custom deserializers
            if (shape.isMapShape() || shape.isListShape()) {

                var symbol = directive.symbolProvider().toSymbol(shape);
                var name = CodegenUtils.getDefaultName(shape, directive.service());

                writer.pushState();
                writer.putContext("schema", SdkSchema.class);
                writer.putContext("name", name);
                writer.putContext("biConsumer", BiConsumer.class);

                writeSerializationClass(writer, symbol, shape, directive);
                // Map shapes need to generate an intermediate wrapper serializer
                if (shape.isMapShape()) {
                    writeMapValueSerializerWrapper(writer, shape.asMapShape().get(), directive);
                }
                writeDeserializerMethod(writer, symbol, shape, directive);
                writer.popState();
            }
        }
    }

    private static void writeSerializationClass(
        JavaWriter writer,
        Symbol symbol,
        Shape shape,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        writer.pushState();
        writer.putContext("shape", symbol);
        writer.putContext("serializer", shape.isMapShape() ? MapSerializer.class : ShapeSerializer.class);
        writer.write(
            """
                static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:T}, ${serializer:T}> {
                    static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                    @Override
                    public void accept(${shape:T} values, ${serializer:T} serializer) {
                        ${C|}
                    }
                }
                """,
            new SerializerGenerator(
                writer,
                directive.symbolProvider(),
                directive.model(),
                shape,
                directive.service()
            )
        );
        writer.popState();
    }

    private static void writeMapValueSerializerWrapper(
        JavaWriter writer,
        MapShape shape,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        var value = directive.model().expectShape(shape.getValue().getTarget());
        // Lists already have generated serializers so we can skip this.
        if (value.isListShape()) {
            return;
        }
        var valueSymbol = directive.symbolProvider().toSymbol(value);
        writer.pushState();
        writer.putContext("shape", valueSymbol);
        writer.putContext("serializer", ShapeSerializer.class);
        writer.write(
            """
                private static final class ${name:U}ValueSerializer implements ${biConsumer:T}<${shape:T}, ${serializer:T}> {
                    static final ${name:U}ValueSerializer INSTANCE = new ${name:U}ValueSerializer();

                    @Override
                    public void accept(${shape:T} values, ${serializer:T} serializer) {
                        ${C|};
                    }
                }
                """,
            new SerializerGenerator.SerializerMemberWriterVisitor(
                writer,
                directive.symbolProvider(),
                directive.model(),
                shape.getValue(),
                "serializer",
                "values",
                directive.service()
            )
        );
        writer.popState();
    }

    private static void writeDeserializerMethod(
        JavaWriter writer,
        Symbol symbol,
        Shape shape,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        writer.pushState();
        writer.putContext("shape", symbol);
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext(
            "collectionImpl",
            symbol.expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
        );
        writer.write(
            """
                static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                    ${shape:T} result = new ${collectionImpl:T}<>();
                    ${C|}
                    return result;
                }
                """,
            new DeserializationMethodVisitor(
                writer,
                directive.symbolProvider(),
                directive.model(),
                directive.service(),
                shape
            )
        );
        writer.popState();
    }

    /**
     * Loops through service closure and finds all shapes that will not generate their own schemas
     *
     * @return shapes that need a shared schema definition
     */
    private static Set<Shape> getCommonShapes(Collection<Shape> connectedShapes) {
        return connectedShapes.stream()
            .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
            .filter(s -> !Prelude.isPreludeShape(s))
            .collect(Collectors.toSet());
    }

    private static String getFilename(JavaCodegenSettings settings) {
        return String.format("./%s/model/SharedSchemas.java", settings.packageNamespace().replace(".", "/"));
    }

    private static final class DeserializationMethodVisitor extends ShapeVisitor.Default<Void> implements Runnable {

        private final JavaWriter writer;
        private final SymbolProvider provider;
        private final Model model;
        private final ServiceShape service;
        private final Shape shape;

        private DeserializationMethodVisitor(
            JavaWriter writer,
            SymbolProvider provider,
            Model model,
            ServiceShape service,
            Shape shape
        ) {
            this.writer = writer;
            this.provider = provider;
            this.model = model;
            this.service = service;
            this.shape = shape;
        }

        @Override
        public void run() {
            shape.accept(this);
        }

        @Override
        protected Void getDefault(Shape shape) {
            throw new IllegalArgumentException("Schema methods are only generated for maps and lists. Found " + shape);
        }

        @Override
        public Void listShape(ListShape shape) {
            var target = model.expectShape(shape.getMember().getTarget());
            writer.write(
                """
                    deserializer.readList(schema, result, (listData, elem) -> {
                        listData.add($C);
                    });""",
                new DeserializerGenerator(
                    writer,
                    target,
                    provider,
                    model,
                    service,
                    "elem",
                    CodegenUtils.getSchemaType(writer, provider, target)
                )
            );
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            // TODO: support maps with non-string keys
            var target = model.expectShape(shape.getValue().getTarget());
            writer.write(
                """
                    deserializer.readStringMap(schema, result, (mapData, key, val) -> {
                        mapData.put(key, $C);
                    });""",
                new DeserializerGenerator(
                    writer,
                    target,
                    provider,
                    model,
                    service,
                    "val",
                    CodegenUtils.getSchemaType(writer, provider, target)
                )
            );
            return null;
        }
    }
}
