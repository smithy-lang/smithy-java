/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ApiServiceGenerator
        implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();
        var delegator = directive.context().writerDelegator();
        var serviceSymbol = directive.symbolProvider().toSymbol(shape);
        var apiSymbol = serviceSymbol.expectProperty(SymbolProperties.SERVICE_API_SERVICE);
        var syntheticShape = StructureShape.builder()
                .id(apiSymbol.getNamespace() + "#" + apiSymbol.getName())
                .addTrait(new DocumentationTrait("Service API schema"))
                .build();

        delegator.useFileWriter(apiSymbol.getDeclarationFile(), apiSymbol.getNamespace(), writer -> {
            writer.pushState(new ClassSection(syntheticShape));
            var template = """
                    public final class ${serviceApiName:L} implements ${apiServiceType:T} {
                        private static final ${serviceApiName:L} $$INSTANCE = new ${serviceApiName:L}();
                        private ${schema:C}

                        /**
                         * Get an instance of this {@code ApiService}.
                         *
                         * @return An instance of this class.
                         */
                        public static ${serviceApiName:L} instance() {
                            return $$INSTANCE;
                        }

                        private ${serviceApiName:L}() {}

                        @Override
                        public ${sdkSchema:N} schema() {
                            return $$SCHEMA;
                        }
                    }""";
            writer.putContext("apiServiceType", ApiService.class);
            writer.putContext("serviceApiName", apiSymbol.getName());
            writer.putContext("sdkSchema", Schema.class);
            writer.putContext("schema", new SchemaFieldGenerator(directive, writer, shape));
            writer.write(template);
            writer.popState();
        });
    }
}
