/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.LinkedHashSet;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SharedSerdeGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var classNames = new LinkedHashSet<String>();
        for (var shape : directive.connectedShapes().values()) {
            if (shape.isListShape() || shape.isMapShape()) {
                classNames.add(getClassName(directive.context(), shape));
            }
        }

        for (var className : classNames) {
            var fileName = CodegenUtils.getJavaFilePath(directive.settings(), "model", className);
            directive.context()
                    .writerDelegator()
                    .useFileWriter(
                            fileName,
                            CodegenUtils.getModelNamespace(directive.settings()),
                            writer -> {
                                writer.putContext("className", className);
                                writer.write(
                                        """
                                                final class ${className:L} {

                                                    ${L@sharedSerde|}

                                                    private ${className:L}() {}
                                                }
                                                """,
                                        "");
                            });
        }
    }

    static String getClassName(CodeGenerationContext context, Shape shape) {
        return getClassName(context.schemaFieldOrder(), shape);
    }

    static String getClassName(SchemaFieldOrder schemaFieldOrder, Shape shape) {
        var field = schemaFieldOrder.getSchemaField(shape.getId());
        if (field == null) {
            return "SharedSerde";
        }
        var schemaClass = field.classRef().className();
        return "SharedSerde" + schemaClass.substring("Schemas".length());
    }
}
