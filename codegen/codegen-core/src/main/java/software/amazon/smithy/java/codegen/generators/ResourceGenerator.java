/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.ApiResource;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.BottomUpIndex;
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
                                private static final ${shape:T} $$INSTANCE = new ${shape:T}();
                                ${properties:C|}
                                private ${schema:C}

                                ${id:C|}

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

                                ${?specificApiServiceType}
                                @Override
                                public ${apiServiceType:T} service() {
                                    return ${specificApiServiceType:T}.instance();
                                }
                                ${/specificApiServiceType}${^specificApiServiceType}
                                @Override
                                public ${apiServiceType:T} service() {
                                    return null;
                                }
                                ${/specificApiServiceType}
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
                                    directive,
                                    writer,
                                    directive.symbolProvider(),
                                    directive.model(),
                                    shape));
                    writer.putContext(
                            "schema",
                            new SchemaFieldGenerator(
                                    directive,
                                    writer,
                                    shape));

                    var bottomUpIndex = BottomUpIndex.of(directive.model());
                    var resourceOptional = bottomUpIndex.getResourceBinding(directive.service(), shape);
                    writer.putContext("hasResource", resourceOptional.isPresent());
                    resourceOptional.ifPresent(
                            resourceShape -> writer.putContext("resource",
                                    directive.symbolProvider().toSymbol(resourceShape)));

                    writer.putContext("apiServiceType", ApiService.class);
                    var apiService = CodegenUtils.tryGetServiceProperty(
                            directive,
                            SymbolProperties.SERVICE_API_SERVICE);
                    if (apiService != null) {
                        writer.putContext("specificApiServiceType", apiService);
                    }

                    writer.write(template);
                    writer.popState();
                });
    }

    private record PropertyGenerator(
            ContextualDirective<CodeGenerationContext, JavaCodegenSettings> directive,
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
                            private static final ${map:T}<${string:T}, ${sdkSchema:T}> $$IDENTIFIERS = ${map:T}.ofEntries(${#ids}${map:T}.entry(${key:S}, ${value:L})${^key.last},
                                ${/key.last}${/ids});
                            private static final ${map:T}<${string:T}, ${sdkSchema:T}> $$PROPERTIES = ${map:T}.ofEntries(${#props}${map:T}.entry(${key:S}, ${value:L})${^key.last},
                                ${/key.last}${/props});
                            """);
            writer.popState();
        }

        private String getSchema(ShapeId value) {
            var target = model.expectShape(value);
            return directive.context().schemaFieldOrder().getSchemaFieldName(target, writer);
        }
    }
}
