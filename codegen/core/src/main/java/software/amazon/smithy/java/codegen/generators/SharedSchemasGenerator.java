/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
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
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
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
        Set<Shape> deferred = deferredShapes(directive.model(), shapes);
        writer.pushState();
        writer.putContext("schemaClass", SdkSchema.class);
        for (var shape : shapes) {
            if (deferred.contains(shape)) {
                writer.write("static final ${schemaClass:T} $L;", CodegenUtils.toSchemaName(shape));
            } else {
                writer.write(
                    "static final ${schemaClass:T} $L = ${C}",
                    CodegenUtils.toSchemaName(shape),
                    new SchemaGenerator(
                        writer,
                        shape,
                        directive.symbolProvider(),
                        directive.model(),
                        directive.context()
                    )
                );
            }
        }
        writer.openBlock("static {", "}", () -> {
            writeDeferred(writer, directive, deferred);
        });
        writer.popState();
    }

    private void writeDeferred(
        JavaWriter writer,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Set<Shape> shapes
    ) {
        Set<Shape> deferred = deferredShapes(directive.model(), shapes);
        for (var shape : shapes) {
            if (deferred.contains(shape)) {
                continue;
            }
            writer.write(
                "$L = ${C}",
                CodegenUtils.toSchemaName(shape),
                new SchemaGenerator(
                    writer,
                    shape,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.context()
                )
            );
        }

        if (!deferred.isEmpty()) {
            writeDeferred(writer, directive, deferred);
        }
    }

    private Set<Shape> deferredShapes(Model model, Set<Shape> shapes) {
        Set<Shape> deferred = new HashSet<>();
        for (var shape : shapes) {
            boolean isDeferred = shape.members()
                .stream()
                .map(MemberShape::getTarget)
                .map(model::expectShape)
                .anyMatch(shapes::contains);
            if (isDeferred) {
                deferred.add(shape);
            }
        }
        return deferred;
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
                writeDeserializerMethod(writer, symbol, shape);
                if (shape.isMapShape()) {
                    writeMapValueDeserializer(writer, symbol, shape.asMapShape().get(), directive);
                } else {
                    writeListMemberDeserializer(writer, symbol, shape.asListShape().get(), directive);
                }
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
        var valueSymbol = directive.symbolProvider().toSymbol(shape.getValue());
        writer.pushState();
        writer.putContext("shape", valueSymbol);
        writer.putContext("serializer", ShapeSerializer.class);
        writer.write(
            """
                private static final class ${name:U}ValueSerializer implements ${biConsumer:T}<${shape:T}, ${serializer:T}> {
                    private static final ${name:U}ValueSerializer INSTANCE = new ${name:U}ValueSerializer();

                    @Override
                    public void accept(${shape:T} values, ${serializer:T} serializer) {
                        ${C|};
                    }
                }
                """,
            new SerializerMemberGenerator(
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
        Shape shape
    ) {
        writer.pushState();
        writer.putContext("shape", symbol);
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext(
            "collectionImpl",
            symbol.expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
        );
        writer.putContext("type", shape.isMapShape() ? "Value" : "Member");
        writer.putContext("reader", shape.isMapShape() ? "readStringMap" : "readList");
        writer.write(
            """
                static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                    ${shape:T} result = new ${collectionImpl:T}<>();
                    deserializer.${reader:L}(schema, result, ${name:U}${type:L}Deserializer.INSTANCE);
                    return result;
                }
                """
        );
        writer.popState();
    }

    private void writeListMemberDeserializer(
        JavaWriter writer,
        Symbol symbol,
        ListShape shape,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        var target = directive.model().expectShape(shape.getMember().getTarget());
        writer.pushState();
        writer.putContext("shape", symbol);
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.write(
            """
                private static final class ${name:U}MemberDeserializer implements ${shapeDeserializer:T}.ListMemberConsumer<${shape:T}> {
                    static final ${name:U}MemberDeserializer INSTANCE = new ${name:U}MemberDeserializer();

                    @Override
                    public void accept(${shape:T} state, ${shapeDeserializer:T} deserializer) {
                        state.add($C);
                    }
                }
                """,
            new DeserializerGenerator(
                writer,
                target,
                directive.symbolProvider(),
                directive.model(),
                directive.service(),
                "deserializer",
                CodegenUtils.getSchemaType(writer, directive.symbolProvider(), target)
            )
        );
        writer.popState();
    }

    private void writeMapValueDeserializer(
        JavaWriter writer,
        Symbol symbol,
        MapShape shape,
        CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        var target = directive.model().expectShape(shape.getValue().getTarget());
        writer.pushState();
        writer.putContext("shape", symbol);
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext("key", directive.symbolProvider().toSymbol(shape.getKey()));
        writer.write(
            """
                private static final class ${name:U}ValueDeserializer implements ${shapeDeserializer:T}.MapMemberConsumer<${key:T}, ${shape:T}> {
                    static final ${name:U}ValueDeserializer INSTANCE = new ${name:U}ValueDeserializer();

                    @Override
                    public void accept(${shape:T} state, ${key:T} key, ${shapeDeserializer:T} deserializer) {
                        state.put(key, $C);
                    }
                }
                """,
            new DeserializerGenerator(
                writer,
                target,
                directive.symbolProvider(),
                directive.model(),
                directive.service(),
                "deserializer",
                CodegenUtils.getSchemaType(writer, directive.symbolProvider(), target)
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
}
