/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.ApiResource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.BottomUpIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ResourceGenerator
        implements Consumer<GenerateResourceDirective<CodeGenerationContext, JavaCodegenSettings>> {
    @Override
    public void accept(GenerateResourceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context()
                .writerDelegator()
                .useShapeWriter(shape, writer -> {
                    writer.pushState(new ClassSection(shape));
                    var template = """
                            public final class ${shape:T} implements ${resourceType:T} {
                                ${id:C|}
                                private static final ${shape:T} $$INSTANCE = new ${shape:T}();
                                ${properties:C|}
                                private ${schema:C}

                                /**
                                 * Get an instance of this {@code ApiResource}.
                                 *
                                 * @return An instance of this class.
                                 */
                                public static ${shape:T} instance() {
                                    return $$INSTANCE;
                                }

                                private ${shape:T}() {}

                                @Override
                                public ${sdkSchema:N} schema() {
                                    return $$SCHEMA;
                                }

                                @Override
                                public ${map:T}<${string:T}, ${sdkSchema:T}> identifiers() {
                                    return $$IDENTIFIERS;
                                }

                                @Override
                                public ${map:T}<${string:T}, ${sdkSchema:T}> properties() {
                                    return $$PROPERTIES;
                                }

                                ${lifecycleOperations:C|}
                                ${?hasCollectionOperations}
                                @Override
                                public ${list:T}<${sdkSchema:T}> collectionOperations() {
                                    return $$COLLECTION_OPERATIONS;
                                }
                                ${/hasCollectionOperations}
                                ${?hasOperations}
                                @Override
                                public ${list:T}<${sdkSchema:T}> operations() {
                                    return $$OPERATIONS;
                                }
                                ${/hasOperations}
                                ${?hasResource}
                                @Override
                                public ${resourceType:T} boundResource() {
                                    return ${resource:T}.instance();
                                }
                                ${/hasResource}
                            }""";
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("id", new IdStringGenerator(writer, shape));
                    writer.putContext("sdkSchema", Schema.class);
                    writer.putContext("map", Map.class);
                    writer.putContext("string", String.class);
                    writer.putContext("list", List.class);
                    writer.putContext("resourceType", ApiResource.class);
                    writer.putContext(
                            "properties",
                            new PropertyGenerator(
                                    writer,
                                    directive.symbolProvider(),
                                    directive.model(),
                                    shape));
                    writer.putContext(
                            "schema",
                            new SchemaGenerator(
                                    writer,
                                    shape,
                                    directive.symbolProvider(),
                                    directive.model(),
                                    directive.context()));
                    writer.putContext(
                            "lifecycleOperations",
                            new LifecycleOperationGenerator(
                                    writer,
                                    directive.symbolProvider(),
                                    directive.model(),
                                    shape));
                    writer.putContext("hasCollectionOperations", !shape.getCollectionOperations().isEmpty());
                    writer.putContext("hasOperations", !shape.getOperations().isEmpty());
                    var bottomUpIndex = BottomUpIndex.of(directive.model());
                    var resourceOptional = bottomUpIndex.getResourceBinding(directive.service(), shape);
                    writer.putContext("hasResource", resourceOptional.isPresent());
                    resourceOptional.ifPresent(
                            resourceShape -> writer.putContext("resource",
                                    directive.symbolProvider().toSymbol(resourceShape)));

                    writer.write(template);
                    writer.popState();
                });
    }

    private record PropertyGenerator(
            JavaWriter writer,
            SymbolProvider symbolProvider,
            Model model,
            ResourceShape resourceShape) implements Runnable {
        @Override
        public void run() {
            Map<String, String> identifiers = new HashMap<>();
            for (var identifierEntry : resourceShape.getIdentifiers().entrySet()) {
                identifiers.put(identifierEntry.getKey(), getSchema(identifierEntry.getValue()));
            }
            Map<String, String> properties = new HashMap<>();
            for (var propertyEntry : resourceShape.getProperties().entrySet()) {
                properties.put(propertyEntry.getKey(), getSchema(propertyEntry.getValue()));
            }
            writer.pushState();
            writer.putContext("ids", identifiers);
            writer.putContext("props", properties);
            writer.write(
                    """
                            private static final ${map:T}<${string:T}, ${sdkSchema:T}> $$IDENTIFIERS = ${map:T}.of(${#ids}${key:S}, ${value:L}${^key.last},
                                ${/key.last}${/ids});
                            private static final ${map:T}<${string:T}, ${sdkSchema:T}> $$PROPERTIES = ${map:T}.of(${#props}${key:S}, ${value:L}${^key.last},
                                ${/key.last}${/props});
                            """);
            if (!resourceShape.getCollectionOperations().isEmpty()) {
                writer.putContext("colOperations", getOperationSymbols(resourceShape.getCollectionOperations()));
                writer.write(
                        """
                                private static final ${list:T}<${sdkSchema:T}>$$COLLECTION_OPERATIONS = ${list:T}.of(${#colOperations}${value:T}.$$SCHEMA${^key.last},
                                    ${/key.last}${/colOperations});""");
            }
            if (!resourceShape.getOperations().isEmpty()) {
                writer.putContext("operations", getOperationSymbols(resourceShape.getOperations()));
                writer.write(
                        """
                                private static final ${list:T}<${sdkSchema:T}> $$OPERATIONS = ${list:T}.of(${#operations}${value:T}.$$SCHEMA${^key.last},
                                    ${/key.last}${/operations});""");
            }
            writer.popState();
        }

        private String getSchema(ShapeId value) {
            var target = model.expectShape(value);
            return CodegenUtils.getSchemaType(writer, symbolProvider, target);
        }

        private List<Symbol> getOperationSymbols(Set<ShapeId> shapeIds) {
            List<Symbol> operations = new ArrayList<>();
            for (var operation : resourceShape.getOperations()) {
                var op = symbolProvider.toSymbol(model.expectShape(operation));
                operations.add(op);
            }
            return operations;
        }
    }

    private record LifecycleOperationGenerator(
            JavaWriter writer,
            SymbolProvider symbolProvider,
            Model model,
            ResourceShape resourceShape) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("shapeId", ShapeId.class);
            resourceShape.getCreate().ifPresent(id -> writeLifecycleOp(id, "create"));
            resourceShape.getPut().ifPresent(id -> writeLifecycleOp(id, "put"));
            resourceShape.getRead().ifPresent(id -> writeLifecycleOp(id, "read"));
            resourceShape.getUpdate().ifPresent(id -> writeLifecycleOp(id, "update"));
            resourceShape.getDelete().ifPresent(id -> writeLifecycleOp(id, "delete"));
            resourceShape.getList().ifPresent(id -> writeLifecycleOp(id, "list"));
            writer.popState();
        }

        private void writeLifecycleOp(ShapeId operationShapeId, String lifecycleOperation) {
            var operationShape = model.expectShape(operationShapeId, OperationShape.class);
            writer.pushState();
            writer.putContext("lifecycleOperation", lifecycleOperation);
            writer.putContext("operation", symbolProvider.toSymbol(operationShape));
            writer.write("""
                    @Override
                    public ${sdkSchema:T} ${lifecycleOperation:L}() {
                        return ${operation:T}.$$SCHEMA;
                    }
                    """);
            writer.newLine();
            writer.popState();
        }
    }
}
