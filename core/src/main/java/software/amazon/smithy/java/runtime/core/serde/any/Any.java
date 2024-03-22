/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public interface Any extends SerializableShape {

    SdkSchema SCHEMA = SdkSchema.builder().id(ShapeId.from("smithy.api#Document")).type(ShapeType.INTEGER).build();

    static Any of(boolean value) {
        return of(value, SCHEMA);
    }

    static Any of(boolean value, SdkSchema schema) {
        return new BooleanAny(value, schema);
    }

    static Any of(byte[] value) {
        return of(value, SCHEMA);
    }

    static Any of(byte[] value, SdkSchema schema) {
        return new BlobAny(value, schema);
    }

    static Any of(Instant value) {
        return of(value, SCHEMA);
    }

    static Any of(Instant value, SdkSchema schema) {
        return new TimestampAny(value, schema);
    }

    static Any of(String value) {
        return of(value, SCHEMA);
    }

    static Any of(String value, SdkSchema schema) {
        return new StringAny(value, schema);
    }

    static Any of(byte value) {
        return of(value, SCHEMA);
    }

    static Any of(byte value, SdkSchema schema) {
        return new ByteAny(value, schema);
    }

    static Any of(short value) {
        return of(value, SCHEMA);
    }

    static Any of(short value, SdkSchema schema) {
        return new ShortAny(value, schema);
    }

    static Any of(int value) {
        return of(value, SCHEMA);
    }

    static Any of(int value, SdkSchema schema) {
        return new IntegerAny(value, schema);
    }

    static Any of(long value) {
        return of(value, SCHEMA);
    }

    static Any of(long value, SdkSchema schema) {
        return new LongAny(value, schema);
    }

    static Any of(float value) {
        return of(value, SCHEMA);
    }

    static Any of(float value, SdkSchema schema) {
        return new FloatAny(value, schema);
    }

    static Any of(double value) {
        return of(value, SCHEMA);
    }

    static Any of(double value, SdkSchema schema) {
        return new DoubleAny(value, schema);
    }

    static Any of(BigInteger value) {
        return of(value, SCHEMA);
    }

    static Any of(BigInteger value, SdkSchema schema) {
        return new BigIntegerAny(value, schema);
    }

    static Any of(BigDecimal value) {
        return of(value, SCHEMA);
    }

    static Any of(BigDecimal value, SdkSchema schema) {
        return new BigDecimalAny(value, schema);
    }

    static Any of(List<Any> value) {
        return of(value, SCHEMA);
    }

    static Any of(List<Any> value, SdkSchema schema) {
        return new ListAny(value, schema);
    }

    static Any of(Map<Any, Any> value) {
        return of(value, SCHEMA);
    }

    static Any of(Map<Any, Any> value, SdkSchema schema) {
        return new MapAny(value, schema);
    }

    static Any of(SerializableShape shape) {
        AnyParser parser = new AnyParser();
        shape.serialize(parser);
        return parser.getResult();
    }

    SdkSchema getSchema();

    ShapeType getType();

    default boolean asBoolean() {
        throw new IllegalStateException("Expected a boolean value, but found " + getType());
    }

    default byte asByte() {
        throw new IllegalStateException("Expected a byte value, but found " + getType());
    }

    default short asShort() {
        throw new IllegalStateException("Expected a short value, but found " + getType());
    }

    default int asInteger() {
        throw new IllegalStateException("Expected an integer value, but found " + getType());
    }

    default long asLong() {
        throw new IllegalStateException("Expected a long value, but found " + getType());
    }

    default float asFloat() {
        throw new IllegalStateException("Expected a float value, but found " + getType());
    }

    default double asDouble() {
        throw new IllegalStateException("Expected a double value, but found " + getType());
    }

    default BigInteger asBigInteger() {
        throw new IllegalStateException("Expected a bigInteger value, but found " + getType());
    }

    default BigDecimal asBigDecimal() {
        throw new IllegalStateException("Expected a bigDecimal value, but found " + getType());
    }

    default Number asNumber() {
        return switch (getType()) {
            case BYTE -> asByte();
            case SHORT -> asShort();
            case INTEGER -> asInteger();
            case LONG -> asLong();
            case FLOAT -> asFloat();
            case DOUBLE -> asDouble();
            case BIG_INTEGER -> asBigInteger();
            case BIG_DECIMAL -> asBigDecimal();
            default -> throw new IllegalStateException("Expected a number value, but found " + getType());
        };
    }

    default String asString() {
        throw new IllegalStateException("Expected a string value, but found " + getType());
    }

    default byte[] asBlob() {
        throw new IllegalStateException("Expected a blob value, but found " + getType());
    }

    default Instant asTimestamp() {
        throw new IllegalStateException("Expected a timestamp value, but found " + getType());
    }

    default List<Any> asList() {
        throw new IllegalStateException("Expected a list value, but found " + getType());
    }

    default Map<Any, Any> asMap() {
        throw new IllegalStateException("Expected a map value, but found " + getType());
    }

    default Any getStructMember(String memberName) {
        throw new IllegalStateException("Expected a structure or union value, but found " + getType());
    }

    default <T extends SerializableShape> void deserializeInto(SdkShapeBuilder<T> builder) {
        builder.deserialize(new AnyDeserializer(this));
    }

    default <T extends SerializableShape> T asShape(SdkShapeBuilder<T> builder) {
        deserializeInto(builder);
        return builder.errorCorrection().build();
    }

    @Override
    default void serialize(ShapeSerializer encoder) {
        new AnySerializer(this).serialize(encoder);
    }
}
