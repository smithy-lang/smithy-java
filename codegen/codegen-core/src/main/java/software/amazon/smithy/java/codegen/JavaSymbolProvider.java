/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static java.lang.String.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.UnitTypeTrait;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Maps Smithy types to Java Symbols, with mode-aware symbol generation for services and operations.
 */
@SmithyInternalApi
public class JavaSymbolProvider implements ShapeVisitor<Symbol>, SymbolProvider {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaSymbolProvider.class);
    private static final Symbol UNIT_SYMBOL = CodegenUtils.fromClass(Unit.class);

    private final Model model;
    private final ServiceShape service;
    private final String packageNamespace;
    private final String serviceName;
    private final Set<CodegenMode> modes;
    private final Map<ShapeId, Symbol> externalTypes;

    public JavaSymbolProvider(
            Model model,
            ServiceShape service,
            String packageNamespace,
            String serviceName,
            Set<CodegenMode> modes
    ) {
        this.model = model;
        this.service = service;
        this.packageNamespace = packageNamespace;
        this.serviceName = serviceName;
        this.modes = modes;
        this.externalTypes = buildExternalTypes();
    }

    private static Map<ShapeId, Symbol> buildExternalTypes() {
        Map<ShapeId, Symbol> mappings = new HashMap<>();
        SchemaIndex.getCombinedSchemaIndex().visit(schema -> {
            if (schema.shapeClass() != null) {
                mappings.put(schema.id(),
                        CodegenUtils.fromClass(schema.shapeClass())
                                .toBuilder()
                                .putProperty(SymbolProperties.EXTERNAL_TYPE, true)
                                .build());
            }
        });
        return Map.copyOf(mappings);
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        // Direct match (structure, union, enum, etc.)
        var ext = externalTypes.get(shape.toShapeId());
        if (ext != null) {
            return ext;
        }
        return switch (shape) {
            case MemberShape ms when externalTypes.containsKey(ms.getTarget()) ->
                externalTypes.get(ms.getTarget());
            case ListShape ls when externalTypes.containsKey(ls.getMember().getTarget()) ->
                resolveSymbol(shape).toBuilder()
                        .references(List.of(new SymbolReference(externalTypes.get(ls.getMember().getTarget()))))
                        .build();
            case MapShape ms when externalTypes.containsKey(ms.getValue().getTarget()) ->
                resolveSymbol(shape).toBuilder()
                        .references(List.of(
                                new SymbolReference(resolveSymbol(ms.getKey())),
                                new SymbolReference(externalTypes.get(ms.getValue().getTarget()))))
                        .build();
            default -> resolveSymbol(shape);
        };
    }

    private Symbol resolveSymbol(Shape shape) {
        Symbol symbol = shape.accept(this);
        LOGGER.trace("Creating symbol from {}: {}", shape, symbol);
        return symbol;
    }

    @Override
    public String toMemberName(MemberShape shape) {
        return CodegenUtils.toMemberName(shape, model);
    }

    @Override
    public Symbol blobShape(BlobShape blobShape) {
        var type = blobShape.hasTrait(StreamingTrait.class) ? DataStream.class : ByteBuffer.class;
        return CodegenUtils.fromClass(type)
                .toBuilder()
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
                .build();
    }

    @Override
    public Symbol booleanShape(BooleanShape booleanShape) {
        return CodegenUtils.fromBoxedClass(boolean.class, Boolean.class);
    }

    @Override
    public Symbol listShape(ListShape listShape) {
        return CodegenUtils.fromClass(List.class)
                .toBuilder()
                .putProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER, "unmodifiableList")
                .putProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, ArrayList.class)
                .putProperty(SymbolProperties.COLLECTION_EMPTY_METHOD, "emptyList()")
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
                .addReference(listShape.getMember().accept(this))
                .build();
    }

    @Override
    public Symbol mapShape(MapShape mapShape) {
        return CodegenUtils.fromClass(Map.class)
                .toBuilder()
                .putProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER, "unmodifiableMap")
                .putProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, LinkedHashMap.class)
                .putProperty(SymbolProperties.COLLECTION_EMPTY_METHOD, "emptyMap()")
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
                .addReference(mapShape.getKey().accept(this))
                .addReference(mapShape.getValue().accept(this))
                .build();
    }

    @Override
    public Symbol byteShape(ByteShape byteShape) {
        return CodegenUtils.fromBoxedClass(byte.class, Byte.class);
    }

    @Override
    public Symbol shortShape(ShortShape shortShape) {
        return CodegenUtils.fromBoxedClass(short.class, Short.class);
    }

    @Override
    public Symbol integerShape(IntegerShape integerShape) {
        return CodegenUtils.fromBoxedClass(int.class, Integer.class);
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return getJavaClassSymbol(shape).toBuilder()
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
                .putProperty(SymbolProperties.ENUM_VALUE_TYPE, integerShape(shape))
                .build();
    }

    @Override
    public Symbol longShape(LongShape longShape) {
        return CodegenUtils.fromBoxedClass(long.class, Long.class);
    }

    @Override
    public Symbol floatShape(FloatShape floatShape) {
        return CodegenUtils.fromBoxedClass(float.class, Float.class);
    }

    @Override
    public Symbol documentShape(DocumentShape documentShape) {
        return CodegenUtils.fromClass(Document.class);
    }

    @Override
    public Symbol doubleShape(DoubleShape doubleShape) {
        return CodegenUtils.fromBoxedClass(double.class, Double.class);
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape bigIntegerShape) {
        return CodegenUtils.fromClass(BigInteger.class);
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape bigDecimalShape) {
        return CodegenUtils.fromClass(BigDecimal.class);
    }

    @Override
    public Symbol timestampShape(TimestampShape timestampShape) {
        return CodegenUtils.fromClass(Instant.class);
    }

    @Override
    public Symbol stringShape(StringShape stringShape) {
        return CodegenUtils.fromClass(String.class);
    }

    @Override
    public Symbol memberShape(MemberShape memberShape) {
        var container = model.getShape(memberShape.getContainer())
                .orElseThrow(
                        () -> new CodegenException(
                                "Could not find shape " + memberShape.getContainer() + " containing "
                                        + memberShape));
        if (container.isEnumShape() || container.isIntEnumShape()) {
            // Adds a property SymbolProperties.ENUM_VARIANT_CLASS_NAME containing the class name
            // for each of the enum variants. The class name is created by converting the enum field
            // name (typically UPPER_SNAKE_CASE like OPTION_ONE) to a class name (PascalCase like
            // OptionOneType). The "Type" suffix avoids conflicts with java. If the resulting
            // name conflicts with the enum class name the suffix value is added after "Type".
            var memberName = CodegenUtils.toMemberName(memberShape, model);
            var className = CaseUtils.toPascalCase(memberName) + "Type";
            var targetName = CodegenUtils.getDefaultName(container, service);
            if (targetName.equals(className)) {
                className = className + "Value";
            }
            Symbol targetSymbol;
            if (container.isEnumShape()) {
                targetSymbol = CodegenUtils.fromClass(String.class);
            } else {
                targetSymbol = CodegenUtils.fromClass(Integer.class);
            }
            return targetSymbol.toBuilder()
                    .putProperty(SymbolProperties.ENUM_VARIANT_CLASS_NAME, className)
                    .build();
        }
        var target = model.getShape(memberShape.getTarget())
                .orElseThrow(
                        () -> new CodegenException(
                                "Could not find shape " + memberShape.getTarget() + " targeted by "
                                        + memberShape));

        if (CodegenUtils.isEventStream(target)) {
            return CodegenUtils.fromClass(EventStream.class)
                    .toBuilder()
                    .addReference(toSymbol(target))
                    .build();
        }
        return toSymbol(target);
    }

    @Override
    public Symbol operationShape(OperationShape operationShape) {
        var baseSymbol = getJavaClassSymbol(operationShape);
        if (modes.contains(CodegenMode.SERVER)) {
            String stubName = baseSymbol.getName() + "Operation";
            String asyncStubName = stubName + "Async";
            String operationFieldName = StringUtils.uncapitalize(baseSymbol.getName());
            var stubSymbol = Symbol.builder()
                    .name(stubName)
                    .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                    .namespace(format("%s.service", packageNamespace), ".")
                    .declarationFile(
                            CodegenUtils.getJavaFilePath(packageNamespace, "service", stubName))
                    .build();
            var asyncStubSymbol = Symbol.builder()
                    .name(asyncStubName)
                    .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                    .namespace(format("%s.service", packageNamespace), ".")
                    .declarationFile(
                            CodegenUtils.getJavaFilePath(packageNamespace, "service", asyncStubName))
                    .build();
            return baseSymbol.toBuilder()
                    .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                    .putProperty(ServerSymbolProperties.OPERATION_FIELD_NAME, operationFieldName)
                    .putProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION, asyncStubSymbol)
                    .putProperty(ServerSymbolProperties.STUB_OPERATION, stubSymbol)
                    .putProperty(ServerSymbolProperties.API_OPERATION, baseSymbol)
                    .build();
        }
        return baseSymbol;
    }

    @Override
    public Symbol resourceShape(ResourceShape resourceShape) {
        return getJavaClassSymbol(resourceShape);
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        if (modes.contains(CodegenMode.CLIENT)) {
            var clientSymbol = getClientServiceSymbol();
            if (modes.contains(CodegenMode.SERVER)) {
                // Combined mode: attach server symbol as a property
                var serverSymbol = getServerServiceSymbol();
                return clientSymbol.toBuilder()
                        .putProperty(ServerSymbolProperties.SERVER_SERVICE_SYMBOL, serverSymbol)
                        .build();
            }
            return clientSymbol;
        }
        if (modes.contains(CodegenMode.SERVER)) {
            return getServerServiceSymbol();
        }
        // TYPES mode: no service symbol
        return null;
    }

    @Override
    public Symbol enumShape(EnumShape shape) {
        return getJavaClassSymbol(shape).toBuilder()
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, false)
                .putProperty(SymbolProperties.ENUM_VALUE_TYPE, stringShape(shape))
                .build();
    }

    @Override
    public Symbol structureShape(StructureShape structureShape) {
        if (structureShape.hasTrait(UnitTypeTrait.class)) {
            return UNIT_SYMBOL;
        }
        return getJavaClassSymbol(structureShape);
    }

    @Override
    public Symbol unionShape(UnionShape unionShape) {
        return getJavaClassSymbol(unionShape);
    }

    protected ServiceShape service() {
        return service;
    }

    protected String packageNamespace() {
        return packageNamespace;
    }

    private Symbol getJavaClassSymbol(Shape shape) {
        String name = CodegenUtils.getDefaultName(shape, service);
        return Symbol.builder()
                .name(name)
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .putProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT, true)
                .namespace(format("%s.model", packageNamespace), ".")
                .declarationFile(CodegenUtils.getJavaFilePath(packageNamespace, "model", name))
                .build();
    }

    private Symbol getClientServiceSymbol() {
        var name = serviceName + "Client";
        var symbol = Symbol.builder()
                .name(name)
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .putProperty(SymbolProperties.SERVICE_EXCEPTION,
                        CodegenUtils.getServiceExceptionSymbol(packageNamespace, serviceName))
                .putProperty(SymbolProperties.SERVICE_API_SERVICE,
                        CodegenUtils.getServiceApiSymbol(packageNamespace, serviceName))
                .namespace(format("%s.client", packageNamespace), ".")
                .definitionFile(CodegenUtils.getJavaFilePath(packageNamespace, "client", name))
                .build();

        return symbol.toBuilder()
                .putProperty(
                        ClientSymbolProperties.CLIENT_IMPL,
                        symbol.toBuilder()
                                .name(name + "Impl")
                                .definitionFile(
                                        CodegenUtils.getJavaFilePath(packageNamespace, "client", name + "Impl"))
                                .build())
                .build();
    }

    private Symbol getServerServiceSymbol() {
        return Symbol.builder()
                .name(serviceName)
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .putProperty(SymbolProperties.SERVICE_EXCEPTION,
                        CodegenUtils.getServiceExceptionSymbol(packageNamespace, serviceName))
                .putProperty(SymbolProperties.SERVICE_API_SERVICE,
                        CodegenUtils.getServiceApiSymbol(packageNamespace, serviceName))
                .putProperty(ServerSymbolProperties.TYPES_NAMESPACE, format("%s.model", packageNamespace))
                .namespace(format("%s.service", packageNamespace), ".")
                .declarationFile(
                        CodegenUtils.getJavaFilePath(packageNamespace, "service", serviceName))
                .build();
    }
}
