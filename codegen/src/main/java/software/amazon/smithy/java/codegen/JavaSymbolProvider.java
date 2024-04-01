/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static java.lang.String.format;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.StreamingTrait;

/**
 * Maps Smithy types to Java Symbols
 */
public final class JavaSymbolProvider implements ShapeVisitor<Symbol>, SymbolProvider {

    private static final System.Logger LOGGER = System.getLogger(JavaSymbolProvider.class.getName());

    private final Model model;
    private final ServiceShape service;
    private final String packageNamespace;

    JavaSymbolProvider(Model model, ServiceShape service, String packageNamespace) {
        this.model = model;
        this.service = service;
        this.packageNamespace = packageNamespace;
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        Symbol symbol = shape.accept(this);
        LOGGER.log(System.Logger.Level.TRACE, () -> format("Creating symbol from %s: %s", shape, symbol));
        return symbol;

    }

    @Override
    public String toMemberName(MemberShape shape) {
        return SymbolProvider.super.toMemberName(shape);
    }

    @Override
    public Symbol blobShape(BlobShape blobShape) {
        if (blobShape.hasTrait(StreamingTrait.class)) {
            return SymbolUtils.fromClass(DataStream.class);
        }
        return SymbolUtils.fromClass(byte[].class);
    }

    @Override
    public Symbol booleanShape(BooleanShape booleanShape) {
        return SymbolUtils.fromBoxedClass(Boolean.class, boolean.class);
    }

    @Override
    public Symbol listShape(ListShape listShape) {
        return SymbolUtils.fromClass(List.class)
                .toBuilder()
                .addReference(listShape.getMember().accept(this))
                .build();
    }

    @Override
    public Symbol mapShape(MapShape mapShape) {
        return SymbolUtils.fromClass(Map.class)
                .toBuilder()
                .addReference(mapShape.getKey().accept(this))
                .addReference(mapShape.getValue().accept(this))
                .build();
    }

    @Override
    public Symbol byteShape(ByteShape byteShape) {
        return SymbolUtils.fromBoxedClass(Byte.class, byte.class);
    }

    @Override
    public Symbol shortShape(ShortShape shortShape) {
        return SymbolUtils.fromBoxedClass(Short.class, short.class);
    }

    @Override
    public Symbol integerShape(IntegerShape integerShape) {
        return SymbolUtils.fromBoxedClass(Integer.class, int.class);
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol longShape(LongShape longShape) {
        return SymbolUtils.fromBoxedClass(Long.class, long.class);
    }

    @Override
    public Symbol floatShape(FloatShape floatShape) {
        return SymbolUtils.fromBoxedClass(Float.class, float.class);
    }

    @Override
    public Symbol documentShape(DocumentShape documentShape) {
        return SymbolUtils.fromClass(Any.class);
    }

    @Override
    public Symbol doubleShape(DoubleShape doubleShape) {
        return SymbolUtils.fromBoxedClass(Double.class, double.class);
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape bigIntegerShape) {
        return SymbolUtils.fromClass(BigIntegerShape.class);
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape bigDecimalShape) {
        return SymbolUtils.fromClass(BigDecimalShape.class);
    }

    @Override
    public Symbol timestampShape(TimestampShape timestampShape) {
        return SymbolUtils.fromClass(Instant.class);
    }

    @Override
    public Symbol stringShape(StringShape stringShape) {
        return SymbolUtils.fromClass(String.class);
    }

    @Override
    public Symbol memberShape(MemberShape memberShape) {
        return toSymbol(
                model.getShape(memberShape.getTarget())
                        .orElseThrow(
                                () -> new CodegenException(
                                        "Could not find shape " + memberShape.getTarget() + " targeted by "
                                                + memberShape
                                )
                        )
        );
    }

    @Override
    public Symbol operationShape(OperationShape operationShape) {
        return null;
    }

    @Override
    public Symbol resourceShape(ResourceShape resourceShape) {
        throw new UnsupportedOperationException("Resource shapes do not generate Java types.");
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        // TODO: implement
        return null;
    }

    @Override
    public Symbol enumShape(EnumShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol structureShape(StructureShape structureShape) {
        return getJavaClassSymbol(structureShape);
    }

    @Override
    public Symbol unionShape(UnionShape unionShape) {
        return getJavaClassSymbol(unionShape);
    }


    private Symbol getJavaClassSymbol(Shape shape) {
        String name = SymbolUtils.getDefaultName(shape, service);
        return Symbol.builder()
                .name(name)
                .namespace(format("%s.model", packageNamespace), ".")
                .declarationFile(format("./%s/model/%s.java", packageNamespace.replace(".", "/"), name))
                .build();
    }
}
