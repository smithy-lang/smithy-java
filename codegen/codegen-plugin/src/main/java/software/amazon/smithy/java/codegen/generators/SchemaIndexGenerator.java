/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SchemaIndexGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var className = "GeneratedSchemaIndex";
        var fileName = CodegenUtils.getJavaFilePath(directive.settings(), "model", className);

        directive.context()
                .writerDelegator()
                .useFileWriter(fileName,
                        CodegenUtils.getModelNamespace(directive.settings()),
                        writer -> generateSchemaIndexClass(writer, className, directive));

        // Generate META-INF/services file
        var serviceFileName = "./resources/META-INF/services/" + SchemaIndex.class.getName();
        var schemaIndexClassName = CodegenUtils.getModelNamespace(directive.settings()) + "." + className;

        directive.context()
                .writerDelegator()
                .useFileWriter(serviceFileName, writer -> writer.write(schemaIndexClassName));
    }

    private void generateSchemaIndexClass(
            JavaWriter writer,
            String className,
            CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {

        writer.putContext("schemaIndex", SchemaIndex.class);
        writer.putContext("schema", Schema.class);
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("map", Map.class);
        writer.putContext("hashMap", HashMap.class);

        // Count total entries for pre-sizing the HashMap
        var order = directive.context().schemaFieldOrder();
        int totalCount = 0;
        for (var shapeOrder : order.partitions()) {
            for (var schemaField : shapeOrder) {
                if (!schemaField.isExternal()) {
                    totalCount++;
                }
            }
        }
        for (var shape : directive.connectedShapes().values()) {
            if ((shape.getType() == ShapeType.ENUM || shape.getType() == ShapeType.INT_ENUM)
                    && !Prelude.isPreludeShape(shape)
                    && !shape.hasTrait(SyntheticTrait.class)) {
                var symbol = directive.symbolProvider().toSymbol(shape);
                if (!symbol.getProperty(SymbolProperties.EXTERNAL_TYPE).orElse(false)) {
                    totalCount++;
                }
            }
        }

        var template =
                """
                        /**
                         * Generated SchemaIndex implementation that provides access to all schemas in the model.
                         */
                        public final class ${className:L} extends ${schemaIndex:T} {

                            private static final ${map:T}<${shapeId:T}, ${schema:T}> SCHEMA_MAP = new ${hashMap:T}<>(${mapCapacity:L});

                            static {
                                ${staticInitCalls:C|}
                            }

                            ${initMethods:C|}

                            @Override
                            public ${schema:T} getSchema(${shapeId:T} id) {
                                return SCHEMA_MAP.get(id);
                            }

                            @Override
                            public void visit(${consumer:T}<${schema:T}> visitor) {
                                SCHEMA_MAP.values().forEach(visitor);
                            }
                        }
                        """;

        writer.pushState();
        writer.putContext("className", className);
        writer.putContext("schemaIndex", SchemaIndex.class);
        writer.putContext("schema", Schema.class);
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("consumer", Consumer.class);
        writer.putContext("mapCapacity", (int) (totalCount / 0.75f) + 1);
        writer.putContext("staticInitCalls", new StaticInitCallsGenerator(writer, directive));
        writer.putContext("initMethods", new InitMethodsGenerator(writer, directive));
        writer.write(template);
        writer.popState();
    }

    /**
     * Generates the calls to partition init methods within the static {} block.
     */
    private record StaticInitCallsGenerator(
            JavaWriter writer,
            CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive)
            implements Runnable {

        @Override
        public void run() {
            var order = directive.context().schemaFieldOrder();
            var partitions = order.partitions();
            for (int i = 0; i < partitions.size(); i++) {
                writer.write("_init$L();", i);
            }
            // Enums get their own init method
            boolean hasEnums = directive.connectedShapes()
                    .values()
                    .stream()
                    .anyMatch(shape -> (shape.getType() == ShapeType.ENUM || shape.getType() == ShapeType.INT_ENUM)
                            && !Prelude.isPreludeShape(shape)
                            && !shape.hasTrait(SyntheticTrait.class));
            if (hasEnums) {
                writer.write("_initEnums();");
            }
        }
    }

    /**
     * Generates the private static init method definitions.
     */
    private record InitMethodsGenerator(
            JavaWriter writer,
            CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive)
            implements Runnable {

        @Override
        public void run() {
            var order = directive.context().schemaFieldOrder();
            var partitions = order.partitions();

            // One method per schema partition
            for (int i = 0; i < partitions.size(); i++) {
                writer.openBlock("private static void _init$L() {", i);
                for (var schemaField : partitions.get(i)) {
                    if (schemaField.isExternal()) {
                        continue;
                    }
                    var schemaReference = schemaField.classRef().className() + "." + schemaField.fieldName();
                    writer.pushState();
                    writer.putContext("schemaReference", schemaReference);
                    writer.write("SCHEMA_MAP.put(${schemaReference:L}.id(), ${schemaReference:L});");
                    writer.popState();
                }
                writer.closeBlock("}");
                writer.write("");
            }

            // Enums init method
            boolean hasEnums = false;
            for (var shape : directive.connectedShapes().values()) {
                if ((shape.getType() == ShapeType.ENUM || shape.getType() == ShapeType.INT_ENUM)
                        && !Prelude.isPreludeShape(shape)
                        && !shape.hasTrait(SyntheticTrait.class)) {
                    hasEnums = true;
                    break;
                }
            }
            if (hasEnums) {
                writer.openBlock("private static void _initEnums() {");
                for (var shape : directive.connectedShapes().values()) {
                    if ((shape.getType() == ShapeType.ENUM || shape.getType() == ShapeType.INT_ENUM)
                            && !Prelude.isPreludeShape(shape)
                            && !shape.hasTrait(SyntheticTrait.class)) {
                        var symbol = directive.symbolProvider().toSymbol(shape);
                        if (symbol.getProperty(SymbolProperties.EXTERNAL_TYPE).orElse(false)) {
                            continue;
                        }
                        writer.pushState();
                        writer.putContext("enumClass", symbol);
                        writer.write("SCHEMA_MAP.put(${enumClass:T}.$$SCHEMA.id(), ${enumClass:T}.$$SCHEMA);");
                        writer.popState();
                    }
                }
                writer.closeBlock("}");
            }
        }
    }
}
