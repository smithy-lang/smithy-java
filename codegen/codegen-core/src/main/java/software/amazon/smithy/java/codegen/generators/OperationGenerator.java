/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiResource;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.BottomUpIndex;
import software.amazon.smithy.model.knowledge.EventStreamIndex;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class OperationGenerator
        implements Consumer<GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context()
                .writerDelegator()
                .useShapeWriter(shape, writer -> {
                    var input =
                            directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getInputShape()));
                    var output =
                            directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getOutputShape()));
                    var eventStreamIndex = EventStreamIndex.of(directive.model());
                    writer.pushState(new ClassSection(shape));
                    writer.putContext("shape", directive.symbol());
                    var template =
                            """
                                    public final class ${shape:T} implements ${operationType:C} {

                                        private static final ${shape:T} $$INSTANCE = new ${shape:T}();

                                        ${schema:C|}

                                        ${id:C|}

                                        ${typeRegistrySection:C|}

                                        private static final ${list:T}<${shapeId:T}> SCHEMES = ${list:T}.of(${#schemes}${shapeId:T}.from(${key:S})${^key.last}, ${/key.last}${/schemes});

                                        ${?idempotencyTokenMember}private static final ${sdkSchema:T} IDEMPOTENCY_TOKEN_MEMBER = ${inputType:T}.$$SCHEMA.member(${idempotencyTokenMember:S});${/idempotencyTokenMember}
                                        ${?inputStreamMember}private static final ${sdkSchema:T} INPUT_STREAM_MEMBER = ${inputType:T}.$$SCHEMA.member(${inputStreamMember:S});${/inputStreamMember}
                                        ${?outputStreamMember}private static final ${sdkSchema:T} OUTPUT_STREAM_MEMBER = ${outputType:T}.$$SCHEMA.member(${outputStreamMember:S});${/outputStreamMember}

                                        /**
                                         * Get an instance of this {@code ApiOperation}.
                                         *
                                         * @return An instance of this class.
                                         */
                                        public static ${shape:T} instance() {
                                            return $$INSTANCE;
                                        }

                                        private ${shape:T}() {}

                                        @Override
                                        public ${sdkShapeBuilder:N}<${inputType:T}> inputBuilder() {
                                            return ${inputType:T}.builder();
                                        }

                                        ${?hasInputEventStream}
                                        @Override
                                        public ${supplier:T}<${sdkShapeBuilder:T}<${inputEventType:T}>> inputEventBuilderSupplier() {
                                            return () -> ${inputEventType:T}.builder();
                                        }
                                        ${/hasInputEventStream}

                                        @Override
                                        public ${sdkShapeBuilder:N}<${outputType:T}> outputBuilder() {
                                            return ${outputType:T}.builder();
                                        }

                                        ${?hasOutputEventStream}
                                        @Override
                                        public ${supplier:T}<${sdkShapeBuilder:T}<${outputEventType:T}>> outputEventBuilderSupplier() {
                                            return () -> ${outputEventType:T}.builder();
                                        }
                                        ${/hasOutputEventStream}

                                        @Override
                                        public ${sdkSchema:N} schema() {
                                            return $$SCHEMA;
                                        }

                                        @Override
                                        public ${sdkSchema:N} inputSchema() {
                                            return ${inputType:T}.$$SCHEMA;
                                        }

                                        @Override
                                        public ${sdkSchema:N} outputSchema() {
                                            return ${outputType:T}.$$SCHEMA;
                                        }

                                        @Override
                                        public ${typeRegistry:N} errorRegistry() {
                                            return TYPE_REGISTRY;
                                        }

                                        @Override
                                        public ${list:T}<${shapeId:T}> effectiveAuthSchemes() {
                                            return SCHEMES;
                                        }

                                        @Override
                                        public ${sdkSchema:T} inputStreamMember() {
                                            return ${?inputStreamMember}INPUT_STREAM_MEMBER${/inputStreamMember}${^inputStreamMember}null${/inputStreamMember};
                                        }

                                        @Override
                                        public ${sdkSchema:T} outputStreamMember() {
                                            return ${?outputStreamMember}OUTPUT_STREAM_MEMBER${/outputStreamMember}${^outputStreamMember}null${/outputStreamMember};
                                        }

                                        @Override
                                        public ${sdkSchema:T} idempotencyTokenMember() {
                                            return ${?idempotencyTokenMember}IDEMPOTENCY_TOKEN_MEMBER${/idempotencyTokenMember}${^idempotencyTokenMember}null${/idempotencyTokenMember};
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
                    writer.putContext("inputType", input);
                    writer.putContext("outputType", output);
                    writer.putContext("id", new IdStringGenerator(writer, shape));
                    writer.putContext("sdkSchema", Schema.class);
                    writer.putContext("shapeId", ShapeId.class);
                    writer.putContext("sdkShapeBuilder", ShapeBuilder.class);
                    writer.putContext("list", List.class);
                    writer.putContext("string", String.class);
                    writer.putContext("set", Set.class);
                    writer.putContext("modeledApiException", ModeledException.class);

                    writer.putContext(
                            "operationType",
                            new OperationTypeGenerator(
                                    writer,
                                    shape,
                                    directive.symbolProvider(),
                                    directive.model(),
                                    eventStreamIndex,
                                    directive.context()));

                    writer.putContext("typeRegistry", TypeRegistry.class);
                    writer.putContext("schema", new SchemaFieldGenerator(directive, writer, shape));
                    var exceptions = getExceptionSymbols(directive.shape(), directive);
                    writer.putContext("exceptions", exceptions);
                    writer.putContext("typeRegistrySection", new TypeRegistryGenerator(writer, exceptions));
                    var serviceIndex = ServiceIndex.of(directive.model());
                    writer.putContext(
                            "schemes",
                            serviceIndex.getEffectiveAuthSchemes(
                                    directive.service(),
                                    shape,
                                    ServiceIndex.AuthSchemeMode.NO_AUTH_AWARE));

                    eventStreamIndex.getInputInfo(shape).ifPresentOrElse(info -> {
                        writer.putContext("supplier", Supplier.class);
                        writer.putContext("hasInputEventStream", true);
                        writer.putContext("inputStreamMember", info.getEventStreamMember().getMemberName());
                        writer.putContext(
                                "inputEventType",
                                directive.symbolProvider().toSymbol(info.getEventStreamTarget()));
                    }, () -> {
                        for (var member : shape.members()) {
                            if (directive.model().expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                                writer.putContext("inputStreamMember", member.getMemberName());
                                break;
                            }
                        }
                    });

                    eventStreamIndex.getOutputInfo(shape).ifPresentOrElse(info -> {
                        writer.putContext("supplier", Supplier.class);
                        writer.putContext("hasOutputEventStream", true);
                        writer.putContext("outputStreamMember", info.getEventStreamMember().getMemberName());
                        writer.putContext(
                                "outputEventType",
                                directive.symbolProvider().toSymbol(info.getEventStreamTarget()));
                    }, () -> {
                        for (var member : shape.members()) {
                            if (directive.model().expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                                writer.putContext("outputStreamMember", member.getMemberName());
                                break;
                            }
                        }
                    });

                    // Add the idempotency token member.
                    for (var member : shape.members()) {
                        if (member.hasTrait(IdempotencyTokenTrait.class)) {
                            writer.putContext("idempotencyTokenMember", member.getMemberName());
                            break;
                        }
                    }

                    var bottomUpIndex = BottomUpIndex.of(directive.model());
                    var resourceOptional = bottomUpIndex.getResourceBinding(directive.service(), shape);
                    writer.putContext("hasResource", resourceOptional.isPresent());
                    writer.putContext("resourceType", ApiResource.class);
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

    private static List<Symbol> getExceptionSymbols(
            OperationShape operation,
            GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        List<Symbol> symbols = new ArrayList<>();
        for (var errorId : operation.getErrors(directive.service())) {
            var shape = directive.model().expectShape(errorId);
            symbols.add(directive.symbolProvider().toSymbol(shape));
        }
        return symbols;
    }

    private record OperationTypeGenerator(
            JavaWriter writer,
            OperationShape shape,
            SymbolProvider symbolProvider,
            Model model,
            EventStreamIndex index,
            CodeGenerationContext context) implements Runnable {
        @Override
        public void run() {
            var inputShape = model.expectShape(shape.getInputShape());
            var input = symbolProvider.toSymbol(inputShape);
            var outputShape = model.expectShape(shape.getOutputShape());
            var output = symbolProvider.toSymbol(outputShape);

            var inputEventStreamInfo = index.getInputInfo(shape);
            var outputEventStreamInfo = index.getOutputInfo(shape);
            inputEventStreamInfo.ifPresent(
                    info -> writer.writeInline(
                            "$1T<$2T, $3T, $4T>",
                            InputEventStreamingApiOperation.class,
                            input,
                            output,
                            symbolProvider.toSymbol(info.getEventStreamTarget())));
            outputEventStreamInfo.ifPresent(info -> {
                if (inputEventStreamInfo.isPresent()) {
                    writer.writeInline(", ");
                }
                writer.writeInline(
                        "$1T<$2T, $3T, $4T>",
                        OutputEventStreamingApiOperation.class,
                        input,
                        output,
                        symbolProvider.toSymbol(info.getEventStreamTarget()));
            });

            if (inputEventStreamInfo.isEmpty() && outputEventStreamInfo.isEmpty()) {
                writer.writeInline("$1T<$2T, $3T>", ApiOperation.class, input, output);
            }
        }
    }
}
