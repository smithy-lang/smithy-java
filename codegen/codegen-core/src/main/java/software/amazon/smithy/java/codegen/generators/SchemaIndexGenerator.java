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
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SchemaIndexGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var className = "GeneratedSchemaIndex";
        var fileName = String
                .format("./%s/model/%s.java", directive.settings().packageNamespace().replace(".", "/"), className);

        directive.context()
                .writerDelegator()
                .useFileWriter(fileName,
                        CodegenUtils.getModelNamespace(directive.settings()),
                        writer -> generateSchemaIndexClass(writer, className, directive));

        // Generate META-INF/services file
        var serviceFileName = "./META-INF/services/" + SchemaIndex.class.getName();
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

        var template = """
                /**
                 * Generated SchemaIndex implementation that provides access to all schemas in the model.
                 */
                public final class ${className:L} extends ${schemaIndex:T} {

                    private static final ${map:T}<${shapeId:T}, ${schema:T}> SCHEMA_MAP = new ${hashMap:T}<>();

                    static {
                        ${schemaInitializers:C|}
                    }

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
        writer.putContext("schemaInitializers", new SchemaInitializersGenerator(writer, directive));
        writer.write(template);
        writer.popState();
    }

    private record SchemaInitializersGenerator(
            JavaWriter writer,
            CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive)
            implements Runnable {

        @Override
        public void run() {
            var order = directive.context().schemaFieldOrder();

            for (var shapeOrder : order.partitions()) {
                for (var schemaField : shapeOrder) {
                    if (schemaField.isExternal()) {
                        continue;
                    }

                    var schemaReference = schemaField.className() + "." + schemaField.fieldName();

                    writer.pushState();
                    writer.putContext("schemaReference", schemaReference);
                    writer.write("SCHEMA_MAP.put(${schemaReference:L}.id(), ${schemaReference:L});");
                    writer.popState();
                }
            }
        }
    }
}
