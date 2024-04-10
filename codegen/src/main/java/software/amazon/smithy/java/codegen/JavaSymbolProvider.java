/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static java.lang.String.format;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
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
import software.amazon.smithy.utils.CaseUtils;

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
        LOGGER.log(System.Logger.Level.TRACE, "Creating symbol from %s: %s", shape, symbol);
        return symbol;

    }

    @Override
    public String toMemberName(MemberShape shape) {
        Shape containerShape = model.expectShape(shape.getContainer());
        if (containerShape.isEnumShape() || containerShape.isIntEnumShape()) {
            return CaseUtils.toSnakeCase(SymbolUtils.MEMBER_ESCAPER.escape(shape.getMemberName()))
                .toUpperCase(Locale.ROOT);
        }

        // If a member name contains an underscore, convert to camel case
        if (shape.getMemberName().contains("_")) {
            return SymbolUtils.MEMBER_ESCAPER.escape(CaseUtils.toCamelCase(shape.getMemberName()));
        }

        return SymbolUtils.MEMBER_ESCAPER.escape(shape.getMemberName());
    }

    @Override
    public Symbol blobShape(BlobShape blobShape) {
        if (blobShape.hasTrait(StreamingTrait.class)) {
            return SymbolUtils.fromClass(DataStream.class);
        }
        return SymbolUtils.fromClass(byte[].class)
            .toBuilder()
            .putProperty(SymbolProperties.IS_JAVA_ARRAY, true)
            .putProperty(SymbolProperties.PRIMITIVE, true)
            .build();
    }

    @Override
    public Symbol booleanShape(BooleanShape booleanShape) {
        return SymbolUtils.fromBoxedClass(boolean.class, Boolean.class);
    }

    @Override
    public Symbol listShape(ListShape listShape) {
        return SymbolUtils.fromClass(List.class)
            .toBuilder()
            .putProperty(SymbolProperties.BUILDER_REF_INITIALIZER, "forList()")
            .addReference(listShape.getMember().accept(this))
            .build();
    }

    @Override
    public Symbol mapShape(MapShape mapShape) {
        return SymbolUtils.fromClass(Map.class)
            .toBuilder()
            .putProperty(SymbolProperties.BUILDER_REF_INITIALIZER, "forOrderedMap()")
            .addReference(mapShape.getKey().accept(this))
            .addReference(mapShape.getValue().accept(this))
            .build();
    }

    @Override
    public Symbol byteShape(ByteShape byteShape) {
        return SymbolUtils.fromBoxedClass(byte.class, Byte.class);
    }

    @Override
    public Symbol shortShape(ShortShape shortShape) {
        return SymbolUtils.fromBoxedClass(short.class, Short.class);
    }

    @Override
    public Symbol integerShape(IntegerShape integerShape) {
        return SymbolUtils.fromBoxedClass(int.class, Integer.class);
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return getJavaClassSymbol(shape);
    }

    @Override
    public Symbol longShape(LongShape longShape) {
        return SymbolUtils.fromBoxedClass(long.class, Long.class);
    }

    @Override
    public Symbol floatShape(FloatShape floatShape) {
        return SymbolUtils.fromBoxedClass(float.class, Float.class);
    }

    @Override
    public Symbol documentShape(DocumentShape documentShape) {
        return SymbolUtils.fromClass(Document.class);
    }

    @Override
    public Symbol doubleShape(DoubleShape doubleShape) {
        return SymbolUtils.fromBoxedClass(double.class, Double.class);
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
        // TODO: Implement
        return null;
    }

    @Override
    public Symbol resourceShape(ResourceShape resourceShape) {
        // Resource shapes do not generate a Java type
        return null;
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
            .putProperty(SymbolProperties.PRIMITIVE, false)
            .namespace(format("%s.model", packageNamespace), ".")
            .declarationFile(format("./%s/model/%s.java", packageNamespace.replace(".", "/"), name))
            .build();
    }
}
