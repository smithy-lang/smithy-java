/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Converts one serializable shape into another without an intermediate wire format or document tree.
 *
 * <p>A transcoder synchronously connects calls made to a {@link ShapeSerializer} by the source shape to calls made
 * to a {@link ShapeDeserializer} by the target builder. Structure and union members are matched by their Smithy
 * member names. During error-correcting conversion, members that do not exist in the target schema are reported to
 * {@link ShapeDeserializer.StructMemberConsumer#unknownMember(Object, String)}. Strict conversion drops source
 * members that do not exist in the target and rejects incompatible or lossy conversions.
 *
 * <p>The deserializers passed to consumers are only valid for the duration of the consumer call. This matches how
 * generated builders consume deserializers and allows a transcoder to reuse cursors based on maximum nesting depth
 * rather than allocating a deserializer for every value.
 *
 * <p>Instances are reusable, but are not thread-safe and cannot perform nested conversions. Use {@link #convert} for
 * a convenience method that creates a transcoder for a single conversion, or reuse an instance with
 * {@link #transcode} to retain its cursor pool between conversions. The corresponding strict operations are
 * {@link #convertStrict} and {@link #transcodeStrict}.
 */
public final class ShapeTranscoder {

    private static final int INITIAL_CURSOR_CAPACITY = 8;

    private Cursor[] cursors = new Cursor[INITIAL_CURSOR_CAPACITY];
    private boolean active;
    private boolean strict;

    /**
     * Convert a shape into another generated shape.
     *
     * @param source Source shape to serialize.
     * @param target Target builder to deserialize into.
     * @param <T> Target shape type.
     * @return the built and error-corrected target shape.
     */
    public static <T extends SerializableShape> T convert(SerializableShape source, ShapeBuilder<T> target) {
        return new ShapeTranscoder().transcode(source, target);
    }

    /**
     * Strictly convert a shape into another generated shape.
     *
     * <p>Strict conversion drops source members that do not exist in the target and rejects incompatible or lossy
     * conversions. Lossless numeric conversions between different Smithy shape types are allowed. The target is
     * built without applying client error correction.
     *
     * @param source Source shape to serialize.
     * @param target Target builder to deserialize into.
     * @param <T> Target shape type.
     * @return the built target shape.
     */
    public static <T extends SerializableShape> T convertStrict(SerializableShape source, ShapeBuilder<T> target) {
        return new ShapeTranscoder().transcodeStrict(source, target);
    }

    /**
     * Convert a shape, reusing this transcoder's cursors.
     *
     * @param source Source shape to serialize.
     * @param target Target builder to deserialize into.
     * @param <T> Target shape type.
     * @return the built and error-corrected target shape.
     * @throws IllegalStateException if this transcoder is already performing a conversion.
     */
    public <T extends SerializableShape> T transcode(SerializableShape source, ShapeBuilder<T> target) {
        return transcode(source, target, false);
    }

    /**
     * Strictly convert a shape, reusing this transcoder's cursors.
     *
     * @param source Source shape to serialize.
     * @param target Target builder to deserialize into.
     * @param <T> Target shape type.
     * @return the built target shape.
     * @throws IllegalStateException if this transcoder is already performing a conversion.
     */
    public <T extends SerializableShape> T transcodeStrict(SerializableShape source, ShapeBuilder<T> target) {
        return transcode(source, target, true);
    }

    private <T extends SerializableShape> T transcode(
            SerializableShape source,
            ShapeBuilder<T> target,
            boolean strict
    ) {
        Objects.requireNonNull(source, "source is null");
        Objects.requireNonNull(target, "target is null");
        if (active) {
            throw new IllegalStateException("ShapeTranscoder is already performing a conversion");
        }

        active = true;
        this.strict = strict;
        var root = cursor(0);
        try {
            source.serialize(root);
            root.requireValue();
            if (strict) {
                root.requireCompatibleSchema(target.schema());
            }
            target.deserialize(root);
        } finally {
            root.clearChain();
            this.strict = false;
            active = false;
        }

        return strict ? target.build() : target.errorCorrection().build();
    }

    private Cursor cursor(int depth) {
        if (depth == cursors.length) {
            cursors = Arrays.copyOf(cursors, cursors.length << 1);
        }

        var cursor = cursors[depth];
        if (cursor == null) {
            cursor = cursors[depth] = new Cursor(this, depth);
        }

        return cursor;
    }

    private enum Kind {
        EMPTY,
        NULL,
        BOOLEAN,
        BYTE,
        SHORT,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        BIG_INTEGER,
        BIG_DECIMAL,
        STRING,
        BLOB,
        TIMESTAMP,
        DOCUMENT,
        DATA_STREAM,
        EVENT_STREAM,
        STRUCT,
        LIST,
        MAP
    }

    private enum Mode {
        CAPTURE, STRUCT, LIST, MAP
    }

    private static final class Cursor implements ShapeSerializer, ShapeDeserializer, MapSerializer {

        private final ShapeTranscoder owner;
        private final int depth;
        private Cursor child;

        private Kind kind = Kind.EMPTY;
        private Mode mode = Mode.CAPTURE;
        private boolean booleanValue;
        private long integralValue;
        private double floatingPointValue;
        private Object objectValue;
        private Schema sourceSchema;
        private Object sourceState;
        private int containerSize = -1;
        private BiConsumer<Object, ShapeSerializer> listWriter;
        private BiConsumer<Object, MapSerializer> mapWriter;

        private Schema targetMember;
        private ShapeTranscoderSchemaExtensions.MemberMapping memberMapping;
        private ShapeTranscoderSchemaExtensions.MemberMapping cachedMemberMapping;
        private Object targetState;
        private Schema targetMapKey;
        private StructMemberConsumer<Object> structReader;
        private ListMemberConsumer<Object> listReader;
        private MapMemberConsumer<String, Object> mapReader;

        Cursor(ShapeTranscoder owner, int depth) {
            this.owner = owner;
            this.depth = depth;
        }

        @Override
        public void close() {}

        void clearChain() {
            for (var cursor = this; cursor != null; cursor = cursor.child) {
                cursor.clear();
            }
        }

        private void clear() {
            kind = Kind.EMPTY;
            mode = Mode.CAPTURE;
            objectValue = null;
            sourceSchema = null;
            sourceState = null;
            containerSize = -1;
            listWriter = null;
            mapWriter = null;
            targetMember = null;
            memberMapping = null;
            targetState = null;
            targetMapKey = null;
            structReader = null;
            listReader = null;
            mapReader = null;
        }

        void requireValue() {
            if (kind == Kind.EMPTY) {
                throw new SerializationException("Shape did not serialize a value");
            }
        }

        private void require(Kind expected) {
            if (kind != expected) {
                throw new SerializationException("Expected " + expected + ", but found " + kind);
            }
        }

        private void require(Kind expected, Schema targetSchema) {
            require(expected);
            requireCompatibleSchema(targetSchema);
        }

        private void requireCompatibleSchema(Schema targetSchema) {
            if (!owner.strict || sourceSchema.type() == targetSchema.type()) {
                return;
            }
            if (isNumericType(sourceSchema.type())
                    && isNumericType(targetSchema.type())
                    && isLosslessNumericConversion(targetSchema.type())) {
                return;
            }
            throw incompatibleTypes(sourceSchema, targetSchema);
        }

        private static void requireMatchingSchemaTypes(Schema sourceSchema, Schema targetSchema) {
            if (sourceSchema.type() != targetSchema.type()) {
                throw incompatibleTypes(sourceSchema, targetSchema);
            }
        }

        private static SerializationException incompatibleTypes(Schema sourceSchema, Schema targetSchema) {
            return new SerializationException(
                    "Strict conversion cannot losslessly convert "
                            + sourceSchema.type() + " to " + targetSchema.type());
        }

        private static boolean isNumericType(ShapeType type) {
            return switch (type) {
                case BYTE,
                        SHORT,
                        INTEGER,
                        INT_ENUM,
                        LONG,
                        FLOAT,
                        DOUBLE,
                        BIG_INTEGER,
                        BIG_DECIMAL ->
                    true;
                default -> false;
            };
        }

        private boolean isLosslessNumericConversion(ShapeType targetType) {
            if (kind == Kind.NULL) {
                return true;
            }
            return switch (targetType) {
                case BYTE -> isExactInteger(Byte.MIN_VALUE, Byte.MAX_VALUE);
                case SHORT -> isExactInteger(Short.MIN_VALUE, Short.MAX_VALUE);
                case INTEGER, INT_ENUM -> isExactInteger(Integer.MIN_VALUE, Integer.MAX_VALUE);
                case LONG -> isExactInteger(Long.MIN_VALUE, Long.MAX_VALUE);
                case FLOAT -> isExactFloatingPoint(true);
                case DOUBLE -> isExactFloatingPoint(false);
                case BIG_INTEGER -> isExactBigInteger();
                case BIG_DECIMAL -> isExactBigDecimal();
                default -> false;
            };
        }

        private boolean isExactInteger(long minimum, long maximum) {
            var value = exactNumericValue();
            if (value == null) {
                return false;
            }
            try {
                var integer = value.toBigIntegerExact();
                return integer.compareTo(BigInteger.valueOf(minimum)) >= 0
                        && integer.compareTo(BigInteger.valueOf(maximum)) <= 0;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        private boolean isExactBigInteger() {
            var value = exactNumericValue();
            if (value == null) {
                return false;
            }
            try {
                var integer = value.toBigIntegerExact();
                return switch (kind) {
                    case FLOAT, DOUBLE -> integer.equals(BigInteger.valueOf((long) floatingPointValue));
                    default -> true;
                };
            } catch (ArithmeticException e) {
                return false;
            }
        }

        private boolean isExactBigDecimal() {
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG, BIG_INTEGER, BIG_DECIMAL -> true;
                case FLOAT, DOUBLE -> Double.isFinite(floatingPointValue);
                default -> false;
            };
        }

        private boolean isExactFloatingPoint(boolean targetFloat) {
            var converted = switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> targetFloat ? (float) integralValue : (double) integralValue;
                case FLOAT, DOUBLE -> targetFloat ? (float) floatingPointValue : floatingPointValue;
                case BIG_INTEGER -> targetFloat
                        ? ((BigInteger) objectValue).floatValue()
                        : ((BigInteger) objectValue).doubleValue();
                case BIG_DECIMAL -> targetFloat
                        ? ((BigDecimal) objectValue).floatValue()
                        : ((BigDecimal) objectValue).doubleValue();
                default -> Double.NaN;
            };
            if ((kind == Kind.FLOAT || kind == Kind.DOUBLE) && !Double.isFinite(floatingPointValue)) {
                return Double.isNaN(floatingPointValue)
                        ? Double.isNaN(converted)
                        : converted == floatingPointValue;
            }
            if (!Double.isFinite(converted)) {
                return false;
            }
            var value = exactNumericValue();
            return value != null && value.compareTo(new BigDecimal(converted)) == 0;
        }

        private BigDecimal exactNumericValue() {
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> BigDecimal.valueOf(integralValue);
                case FLOAT, DOUBLE -> Double.isFinite(floatingPointValue)
                        ? new BigDecimal(floatingPointValue)
                        : null;
                case BIG_INTEGER -> new BigDecimal((BigInteger) objectValue);
                case BIG_DECIMAL -> (BigDecimal) objectValue;
                default -> null;
            };
        }

        private void prepareCapture(Schema schema, Kind capturedKind) {
            if (mode != Mode.CAPTURE) {
                throw new SerializationException("Cannot capture a value while forwarding " + mode);
            } else if (kind != Kind.EMPTY) {
                throw new SerializationException("A shape serialized more than one value");
            }

            sourceSchema = schema;
            kind = capturedKind;
        }

        private Cursor beginValue(Schema sourceSchema) {
            switch (mode) {
                case STRUCT -> {
                    var memberName = sourceSchema.memberName();
                    targetMember = memberMapping.targetMember(sourceSchema);
                    if (targetMember == null) {
                        if (!owner.strict) {
                            structReader.unknownMember(targetState, memberName);
                        }
                        return null;
                    }
                }
                case LIST -> {
                }
                default -> throw new SerializationException("Cannot write a value while forwarding " + mode);
            }

            var result = child();
            result.sourceSchema = sourceSchema;
            return result;
        }

        private Cursor child() {
            var result = child;
            if (result == null) {
                child = result = owner.cursor(depth + 1);
            }
            return result;
        }

        private void emitValue(Cursor value) {
            if (owner.strict) {
                value.requireCompatibleSchema(targetMember);
            }
            switch (mode) {
                case STRUCT -> structReader.accept(targetState, targetMember, value);
                case LIST -> listReader.accept(targetState, value);
                default -> throw new SerializationException("Cannot emit a value while forwarding " + mode);
            }
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, Kind.STRUCT);
                objectValue = struct;
                return;
            }
            var value = beginValue(schema);
            if (value != null) {
                value.kind = Kind.STRUCT;
                value.objectValue = struct;
                emitValue(value);
            }
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            if (mode == Mode.CAPTURE) {
                captureList(schema, listState, size, consumer);
                return;
            }
            var value = beginValue(schema);
            if (value != null) {
                value.captureListUnchecked(listState, size, consumer);
                emitValue(value);
            }
        }

        private <T> void captureList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
            prepareCapture(schema, Kind.LIST);
            captureListUnchecked(state, size, consumer);
        }

        @SuppressWarnings("unchecked")
        private <T> void captureListUnchecked(T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
            kind = Kind.LIST;
            sourceState = state;
            containerSize = size;
            listWriter = (BiConsumer<Object, ShapeSerializer>) consumer;
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            if (mode == Mode.CAPTURE) {
                captureMap(schema, mapState, size, consumer);
                return;
            }
            var value = beginValue(schema);
            if (value != null) {
                value.captureMapUnchecked(mapState, size, consumer);
                emitValue(value);
            }
        }

        private <T> void captureMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
            prepareCapture(schema, Kind.MAP);
            captureMapUnchecked(state, size, consumer);
        }

        @SuppressWarnings("unchecked")
        private <T> void captureMapUnchecked(T state, int size, BiConsumer<T, MapSerializer> consumer) {
            kind = Kind.MAP;
            sourceState = state;
            containerSize = size;
            mapWriter = (BiConsumer<Object, MapSerializer>) consumer;
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, Kind.BOOLEAN);
                booleanValue = value;
            } else {
                var cursor = beginValue(schema);
                if (cursor != null) {
                    cursor.kind = Kind.BOOLEAN;
                    cursor.booleanValue = value;
                    emitValue(cursor);
                }
            }
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeIntegral(schema, Kind.BYTE, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeIntegral(schema, Kind.SHORT, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeIntegral(schema, Kind.INTEGER, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeIntegral(schema, Kind.LONG, value);
        }

        private void writeIntegral(Schema schema, Kind numberKind, long value) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, numberKind);
                integralValue = value;
            } else {
                var cursor = beginValue(schema);
                if (cursor != null) {
                    cursor.kind = numberKind;
                    cursor.integralValue = value;
                    emitValue(cursor);
                }
            }
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            writeFloatingPoint(schema, Kind.FLOAT, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            writeFloatingPoint(schema, Kind.DOUBLE, value);
        }

        private void writeFloatingPoint(Schema schema, Kind numberKind, double value) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, numberKind);
                floatingPointValue = value;
            } else {
                var cursor = beginValue(schema);
                if (cursor != null) {
                    cursor.kind = numberKind;
                    cursor.floatingPointValue = value;
                    emitValue(cursor);
                }
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeObject(schema, Kind.BIG_INTEGER, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeObject(schema, Kind.BIG_DECIMAL, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeObject(schema, Kind.STRING, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeObject(schema, Kind.BLOB, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            writeObject(schema, Kind.TIMESTAMP, value);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            writeObject(schema, Kind.DOCUMENT, value);
        }

        @Override
        public void writeDataStream(Schema schema, DataStream value) {
            writeObject(schema, Kind.DATA_STREAM, value);
        }

        @Override
        public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
            writeObject(schema, Kind.EVENT_STREAM, value);
        }

        private void writeObject(Schema schema, Kind valueKind, Object value) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, valueKind);
                objectValue = value;
            } else {
                var cursor = beginValue(schema);
                if (cursor != null) {
                    cursor.kind = valueKind;
                    cursor.objectValue = value;
                    emitValue(cursor);
                }
            }
        }

        @Override
        public void writeNull(Schema schema) {
            if (mode == Mode.CAPTURE) {
                prepareCapture(schema, Kind.NULL);
            } else {
                var cursor = beginValue(schema);
                if (cursor != null) {
                    cursor.kind = Kind.NULL;
                    emitValue(cursor);
                }
            }
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            if (mode != Mode.MAP) {
                throw new SerializationException("Cannot write a map entry while forwarding " + mode);
            }

            var value = child();
            value.kind = Kind.EMPTY;
            valueSerializer.accept(state, value);
            value.requireValue();
            if (owner.strict) {
                requireMatchingSchemaTypes(keySchema, targetMapKey);
                value.requireCompatibleSchema(targetMember);
            }

            mapReader.accept(targetState, key, value);
        }

        @Override
        public boolean readBoolean(Schema schema) {
            require(Kind.BOOLEAN, schema);
            return booleanValue;
        }

        @Override
        public byte readByte(Schema schema) {
            requireCompatibleSchema(schema);
            return (byte) readLongNumber();
        }

        @Override
        public short readShort(Schema schema) {
            requireCompatibleSchema(schema);
            return (short) readLongNumber();
        }

        @Override
        public int readInteger(Schema schema) {
            requireCompatibleSchema(schema);
            return (int) readLongNumber();
        }

        @Override
        public long readLong(Schema schema) {
            requireCompatibleSchema(schema);
            return readLongNumber();
        }

        private long readLongNumber() {
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> integralValue;
                case FLOAT, DOUBLE -> (long) floatingPointValue;
                case BIG_INTEGER -> ((BigInteger) objectValue).longValue();
                case BIG_DECIMAL -> ((BigDecimal) objectValue).longValue();
                default -> throw new SerializationException("Expected a number, but found " + kind);
            };
        }

        @Override
        public float readFloat(Schema schema) {
            requireCompatibleSchema(schema);
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> integralValue;
                case FLOAT, DOUBLE -> (float) floatingPointValue;
                case BIG_INTEGER -> ((BigInteger) objectValue).floatValue();
                case BIG_DECIMAL -> ((BigDecimal) objectValue).floatValue();
                default -> throw new SerializationException("Expected a number, but found " + kind);
            };
        }

        @Override
        public double readDouble(Schema schema) {
            requireCompatibleSchema(schema);
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> integralValue;
                case FLOAT, DOUBLE -> floatingPointValue;
                case BIG_INTEGER -> ((BigInteger) objectValue).doubleValue();
                case BIG_DECIMAL -> ((BigDecimal) objectValue).doubleValue();
                default -> throw new SerializationException("Expected a number, but found " + kind);
            };
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            requireCompatibleSchema(schema);
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> BigInteger.valueOf(integralValue);
                case FLOAT, DOUBLE -> BigInteger.valueOf((long) floatingPointValue);
                case BIG_INTEGER -> (BigInteger) objectValue;
                case BIG_DECIMAL -> ((BigDecimal) objectValue).toBigInteger();
                default -> throw new SerializationException("Expected a number, but found " + kind);
            };
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            requireCompatibleSchema(schema);
            return switch (kind) {
                case BYTE, SHORT, INTEGER, LONG -> BigDecimal.valueOf(integralValue);
                case FLOAT, DOUBLE -> BigDecimal.valueOf(floatingPointValue);
                case BIG_INTEGER -> new BigDecimal((BigInteger) objectValue);
                case BIG_DECIMAL -> (BigDecimal) objectValue;
                default -> throw new SerializationException("Expected a number, but found " + kind);
            };
        }

        @Override
        public String readString(Schema schema) {
            require(Kind.STRING, schema);
            return (String) objectValue;
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            require(Kind.BLOB, schema);
            return (ByteBuffer) objectValue;
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            require(Kind.TIMESTAMP, schema);
            return (Instant) objectValue;
        }

        @Override
        public Document readDocument() {
            require(Kind.DOCUMENT);
            return (Document) objectValue;
        }

        @Override
        public DataStream readDataStream(Schema schema) {
            require(Kind.DATA_STREAM, schema);
            return (DataStream) objectValue;
        }

        @Override
        public EventStream<? extends SerializableStruct> readEventStream(Schema schema) {
            require(Kind.EVENT_STREAM, schema);
            return (EventStream<? extends SerializableStruct>) objectValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            require(Kind.STRUCT, schema);
            var struct = (SerializableStruct) objectValue;
            mode = Mode.STRUCT;
            var sourceSchema = struct.schema();
            var mapping = cachedMemberMapping;
            if (mapping == null || !mapping.matches(sourceSchema, schema)) {
                cachedMemberMapping = mapping = ShapeTranscoderSchemaExtensions.mapping(sourceSchema, schema);
            }

            memberMapping = mapping;
            targetState = state;
            structReader = (StructMemberConsumer<Object>) consumer;

            try {
                struct.serializeMembers(this);
            } finally {
                clearForwardingState();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            require(Kind.LIST, schema);
            mode = Mode.LIST;
            targetMember = schema.listMember();
            targetState = state;
            listReader = (ListMemberConsumer<Object>) consumer;

            try {
                listWriter.accept(sourceState, this);
            } finally {
                clearForwardingState();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            require(Kind.MAP, schema);
            mode = Mode.MAP;
            targetMapKey = schema.mapKeyMember();
            targetMember = schema.mapValueMember();
            targetState = state;
            mapReader = (MapMemberConsumer<String, Object>) consumer;

            try {
                mapWriter.accept(sourceState, this);
            } finally {
                clearForwardingState();
            }
        }

        private void clearForwardingState() {
            var previousMode = mode;
            mode = Mode.CAPTURE;
            targetState = null;
            targetMember = null;
            switch (previousMode) {
                case STRUCT -> {
                    memberMapping = null;
                    structReader = null;
                }
                case LIST -> listReader = null;
                case MAP -> {
                    targetMapKey = null;
                    mapReader = null;
                }
                default -> {
                }
            }
        }

        @Override
        public int containerSize() {
            return kind == Kind.LIST || kind == Kind.MAP ? containerSize : -1;
        }

        @Override
        public boolean isNull() {
            return kind == Kind.NULL;
        }

        @Override
        public <T> T readNull() {
            require(Kind.NULL);
            return null;
        }
    }
}
