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
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.AbstractApiOperation;
import software.amazon.smithy.java.core.schema.ApiResource;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
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
                                    public final class ${shape:T} extends ${abstractApiOperation:T}<${inputType:T}, ${outputType:T}> {

                                        public static final ${shapeId:T} $$ID = ${shapeId:T}.from(${shapeIdValue:S});
                                        public static final ${sdkSchema:T} $$SCHEMA = ${operationSchema:C};

                                        ${?hasExceptions}${typeRegistrySection:C|}
                                        ${/hasExceptions}

                                        ${?hasMultipleSchemes}private static final ${list:T}<${shapeId:T}> SCHEMES = ${list:T}.of(${#schemes}${shapeId:T}.from(${key:S})${^key.last}, ${/key.last}${/schemes});${/hasMultipleSchemes}

                                        ${?idempotencyTokenMember}private static final ${sdkSchema:T} IDEMPOTENCY_TOKEN_MEMBER = ${inputType:T}.$$SCHEMA.member(${idempotencyTokenMember:S});${/idempotencyTokenMember}
                                        ${?inputStreamMember}private static final ${sdkSchema:T} INPUT_STREAM_MEMBER = ${inputType:T}.$$SCHEMA.member(${inputStreamMember:S});${/inputStreamMember}
                                        ${?outputStreamMember}private static final ${sdkSchema:T} OUTPUT_STREAM_MEMBER = ${outputType:T}.$$SCHEMA.member(${outputStreamMember:S});${/outputStreamMember}

                                        private static final ${shape:T} $$INSTANCE = new ${shape:T}();

                                        /**
                                         * Get an instance of this {@code ApiOperation}.
                                         *
                                         * @return An instance of this class.
                                         */
                                        public static ${shape:T} instance() {
                                            return $$INSTANCE;
                                        }

                                        private ${shape:T}() {
                                            super($$SCHEMA${?hasExceptions}, TYPE_REGISTRY${/hasExceptions}${?singleScheme}, ${shapeId:T}.from(${singleScheme:S})${/singleScheme}${?hasMultipleSchemes}, SCHEMES${/hasMultipleSchemes});
                                        }

                                        @Override
                                        public ${sdkSchema:T} inputSchema() {
                                            return ${inputType:T}.$$SCHEMA;
                                        }

                                        @Override
                                        public ${sdkSchema:T} outputSchema() {
                                            return ${outputType:T}.$$SCHEMA;
                                        }

                                        ${?hasExceptions}@Override
                                        public ${list:T}<${sdkSchema:T}> errorSchemas() {
                                            return ${list:T}.of(${#exceptions}${value:T}.$$SCHEMA${^key.last}, ${/key.last}${/exceptions});
                                        }
                                        ${/hasExceptions}

                                        ${?hasInputEventStream}
                                        @Override
                                        public ${supplier:T}<${sdkShapeBuilder:T}<? extends ${serializableStruct:T}>> inputEventBuilderSupplier() {
                                            return () -> ${inputEventType:T}.builder();
                                        }
                                        ${/hasInputEventStream}

                                        ${?hasOutputEventStream}
                                        @Override
                                        public ${supplier:T}<${sdkShapeBuilder:T}<? extends ${serializableStruct:T}>> outputEventBuilderSupplier() {
                                            return () -> ${outputEventType:T}.builder();
                                        }
                                        ${/hasOutputEventStream}

                                        ${?inputStreamMember}
                                        @Override
                                        public ${sdkSchema:T} inputStreamMember() {
                                            return INPUT_STREAM_MEMBER;
                                        }
                                        ${/inputStreamMember}

                                        ${?outputStreamMember}
                                        @Override
                                        public ${sdkSchema:T} outputStreamMember() {
                                            return OUTPUT_STREAM_MEMBER;
                                        }
                                        ${/outputStreamMember}

                                        ${?idempotencyTokenMember}
                                        @Override
                                        public ${sdkSchema:T} idempotencyTokenMember() {
                                            return IDEMPOTENCY_TOKEN_MEMBER;
                                        }
                                        ${/idempotencyTokenMember}

                                        ${?specificApiServiceType}
                                        @Override
                                        public ${apiServiceType:T} service() {
                                            return ${specificApiServiceType:T}.instance();
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
                    writer.putContext("abstractApiOperation", AbstractApiOperation.class);
                    writer.putContext("apiServiceType", ApiService.class);
                    writer.putContext("sdkSchema", Schema.class);
                    writer.putContext("shapeId", ShapeId.class);
                    writer.putContext("shapeIdValue", shape.getId().toString());
                    writer.putContext("sdkShapeBuilder", ShapeBuilder.class);
                    writer.putContext("list", List.class);
                    writer.putContext("string", String.class);
                    writer.putContext("set", Set.class);
                    writer.putContext("modeledApiException", ModeledException.class);

                    writer.putContext("typeRegistry", TypeRegistry.class);
                    writer.putContext(
                            "operationSchema",
                            new OperationSchemaGenerator(writer, shape, directive.context()));
                    var exceptions = getExceptionSymbols(directive.shape(), directive);
                    writer.putContext("exceptions", exceptions);
                    writer.putContext("hasExceptions", !exceptions.isEmpty());
                    writer.putContext("typeRegistrySection", new TypeRegistryGenerator(writer, exceptions));
                    var serviceIndex = ServiceIndex.of(directive.model());
                    var schemes = serviceIndex.getEffectiveAuthSchemes(
                            directive.expectService(),
                            shape,
                            ServiceIndex.AuthSchemeMode.NO_AUTH_AWARE);
                    var apiService = CodegenUtils.tryGetServiceProperty(
                            directive,
                            SymbolProperties.SERVICE_API_SERVICE);
                    writer.putContext("hasMultipleSchemes", schemes.size() > 1);
                    writer.putContext("schemes", schemes);
                    if (schemes.size() == 1) {
                        writer.putContext("singleScheme", schemes.keySet().iterator().next().toString());
                    }

                    var inputShape = directive.model().expectShape(shape.getInputShape());
                    eventStreamIndex.getInputInfo(shape).ifPresentOrElse(info -> {
                        writer.putContext("supplier", Supplier.class);
                        writer.putContext("serializableStruct", SerializableStruct.class);
                        writer.putContext("hasInputEventStream", true);
                        writer.putContext("inputStreamMember", info.getEventStreamMember().getMemberName());
                        writer.putContext(
                                "inputEventType",
                                directive.symbolProvider().toSymbol(info.getEventStreamTarget()));
                    }, () -> {
                        for (var member : inputShape.members()) {
                            if (directive.model().expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                                writer.putContext("inputStreamMember", member.getMemberName());
                                break;
                            }
                        }
                    });

                    eventStreamIndex.getOutputInfo(shape).ifPresentOrElse(info -> {
                        writer.putContext("supplier", Supplier.class);
                        writer.putContext("serializableStruct", SerializableStruct.class);
                        writer.putContext("hasOutputEventStream", true);
                        writer.putContext("outputStreamMember", info.getEventStreamMember().getMemberName());
                        writer.putContext(
                                "outputEventType",
                                directive.symbolProvider().toSymbol(info.getEventStreamTarget()));
                    }, () -> {
                        for (var member : directive.model().expectShape(shape.getOutputShape()).members()) {
                            if (directive.model().expectShape(member.getTarget()).hasTrait(StreamingTrait.class)) {
                                writer.putContext("outputStreamMember", member.getMemberName());
                                break;
                            }
                        }
                    });

                    // Add the idempotency token member.
                    for (var member : inputShape.members()) {
                        if (member.hasTrait(IdempotencyTokenTrait.class)) {
                            writer.putContext("idempotencyTokenMember", member.getMemberName());
                            break;
                        }
                    }

                    var bottomUpIndex = BottomUpIndex.of(directive.model());
                    var resourceOptional =
                            bottomUpIndex.getResourceBinding(directive.expectService(), shape);
                    writer.putContext("hasResource", resourceOptional.isPresent());
                    writer.putContext("resourceType", ApiResource.class);
                    resourceOptional.ifPresent(
                            resourceShape -> writer.putContext("resource",
                                    directive.symbolProvider().toSymbol(resourceShape)));

                    if (apiService != null) {
                        writer.putContext("specificApiServiceType", apiService);
                    }

                    writer.writeNullMarkedAnnotation();
                    writer.write(template);
                    writer.popState();
                });
    }

    private static List<Symbol> getExceptionSymbols(
            OperationShape operation,
            GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        List<Symbol> symbols = new ArrayList<>();
        for (var errorId : operation.getErrors(directive.expectService())) {
            var shape = directive.model().expectShape(errorId);
            symbols.add(directive.symbolProvider().toSymbol(shape));
        }
        return symbols;
    }

    private record OperationSchemaGenerator(
            JavaWriter writer,
            OperationShape shape,
            CodeGenerationContext context) implements Runnable {
        @Override
        public void run() {
            writer.pushState();
            writer.putContext("schema", Schema.class);
            writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
            writer.writeInline("${schema:T}.createOperation($$ID${traits:C})");
            writer.popState();
        }
    }
}
