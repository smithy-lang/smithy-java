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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Flow;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.document.Document;
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
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.UnitTypeTrait;
import software.amazon.smithy.utils.CaseUtils;

/**
 * Maps Smithy types to Java Symbols
 */
public class JavaSymbolProvider implements ShapeVisitor<Symbol>, SymbolProvider {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaSymbolProvider.class);
    private static final Symbol UNIT_SYMBOL = CodegenUtils.fromClass(Unit.class);
    private static final List<String> DELIMITERS = List.of("_", "-", " ");

    private final Model model;
    private final ServiceShape service;
    private final String packageNamespace;

    public JavaSymbolProvider(Model model, ServiceShape service, String packageNamespace) {
        this.model = model;
        this.service = service;
        this.packageNamespace = packageNamespace;
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        Symbol symbol = shape.accept(this);
        LOGGER.trace("Creating symbol from {}: {}", shape, symbol);
        return symbol;
    }

    @Override
    public String toMemberName(MemberShape shape) {
        Shape containerShape = model.expectShape(shape.getContainer());
        if (containerShape.isEnumShape() || containerShape.isIntEnumShape()) {
            return CaseUtils.toSnakeCase(shape.getMemberName()).toUpperCase(Locale.ENGLISH);
        }

        // If a member name contains an underscore, space, or dash, convert to camel case using Smithy utility
        var memberName = shape.getMemberName();
        if (DELIMITERS.stream().anyMatch(memberName::contains)) {
            return CodegenUtils.MEMBER_ESCAPER.escape(CaseUtils.toCamelCase(memberName));
        }

        return CodegenUtils.MEMBER_ESCAPER.escape(uncapitalizeAcronymAware(memberName));
    }

    private static String uncapitalizeAcronymAware(String str) {
        int strLen = str.length();
        StringBuilder sb = new StringBuilder(strLen);
        boolean nextIsUpperCase;
        for (int idx = 0; idx < strLen; idx++) {
            var currentChar = str.charAt(idx);
            nextIsUpperCase = (idx + 1 < strLen) && Character.isUpperCase(str.charAt(idx + 1));
            if (Character.isUpperCase(currentChar) && (nextIsUpperCase || idx == 0)) {
                sb.append(Character.toLowerCase(currentChar));
            } else {
                sb.append(currentChar);
            }
        }
        return sb.toString();
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
        var target = model.getShape(memberShape.getTarget())
            .orElseThrow(
                () -> new CodegenException(
                    "Could not find shape " + memberShape.getTarget() + " targeted by "
                        + memberShape
                )
            );

        if (CodegenUtils.isEventStream(target)) {
            return CodegenUtils.fromClass(Flow.Publisher.class)
                .toBuilder()
                .addReference(toSymbol(target))
                .build();
        }
        return toSymbol(target);
    }

    @Override
    public Symbol operationShape(OperationShape operationShape) {
        return getJavaClassSymbol(operationShape);
    }

    @Override
    public Symbol resourceShape(ResourceShape resourceShape) {
        return getJavaClassSymbol(resourceShape);
    }

    /**
     * @implNote Code generators that need to create service shapes should extend the {@link JavaSymbolProvider} class
     * and override this method.
     */
    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        // Service shape not supported by default java symbol provider.
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
            .declarationFile(format("./%s/model/%s.java", packageNamespace.replace(".", "/"), name))
            .build();
    }
}
