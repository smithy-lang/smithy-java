/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;
import software.amazon.smithy.java.codegen.rt.WriterContext;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.smithy.JsonParseState;
import software.amazon.smithy.java.json.smithy.JsonReadUtils;

/**
 * Static runtime helpers called by generated JSON serializer/deserializer code.
 *
 * <p>These methods handle complex cases that are not worth inlining into every generated class,
 * such as nested struct dispatch, enum resolution, and document handling.
 */
public final class JsonCodegenHelpers {

    private JsonCodegenHelpers() {}

    // Cache for enum's from(String) method handles, keyed by enum class.
    private static final ClassValue<MethodHandle> ENUM_FROM_STRING = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup()
                        .findStatic(
                                type,
                                "from",
                                MethodType.methodType(type, String.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Cannot find from(String) on " + type.getName(), e);
            }
        }
    };

    // Cache for int enum's from(int) method handles, keyed by enum class.
    private static final ClassValue<MethodHandle> ENUM_FROM_INT = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup()
                        .findStatic(
                                type,
                                "from",
                                MethodType.methodType(type, int.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Cannot find from(int) on " + type.getName(), e);
            }
        }
    };

    // Cache for getting $SCHEMA field from shape classes
    private static final ClassValue<Schema> SCHEMA_CACHE = new ClassValue<>() {
        @Override
        protected Schema computeValue(Class<?> type) {
            try {
                return (Schema) MethodHandles.publicLookup()
                        .findStaticGetter(type, "$SCHEMA", Schema.class)
                        .invoke();
            } catch (Throwable e) {
                throw new RuntimeException("Cannot access $SCHEMA on " + type.getName(), e);
            }
        }
    };

    /**
     * Serializes a nested struct/union using the registry for specialized dispatch.
     * Falls back to generic serialization if no specialized serializer is registered.
     */
    public static void serializeNestedStruct(Object obj, WriterContext ctx) {
        if (obj == null) {
            writeNull(ctx.buf, ctx.pos);
            ctx.pos += 4;
            return;
        }

        if (obj instanceof SerializableStruct struct) {
            Schema schema = struct.schema();
            Class<?> shapeClass = obj.getClass();
            GeneratedStructSerializer ser = ctx.registry.getSerializer(schema, shapeClass);
            if (ser != null) {
                ser.serialize(obj, ctx);
                return;
            }
        }
        serializeViaDispatch((SerializableShape) obj, ctx);
    }

    /**
     * Deserializes a nested struct/union using the registry for specialized dispatch.
     */
    public static Object deserializeNestedStruct(JsonReaderContext ctx, Class<?> structClass) {
        Schema schema = SCHEMA_CACHE.get(structClass);
        GeneratedStructDeserializer de = ctx.registry.getDeserializer(schema, structClass);
        if (de != null) {
            ShapeBuilder<?> builder = schema.shapeBuilder();
            return de.deserialize(ctx, builder);
        } else {
            return deserializeViaDispatch(ctx, schema);
        }
    }

    /**
     * Resolves a string enum value using the cached from(String) method handle.
     */
    public static Object resolveEnum(String value, Class<?> enumClass) {
        try {
            return ENUM_FROM_STRING.get(enumClass).invoke(value);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to resolve enum value '" + value + "' for " + enumClass.getName(), e);
        }
    }

    /**
     * Resolves an int enum value using the cached from(int) method handle.
     */
    public static Object resolveIntEnum(int value, Class<?> enumClass) {
        try {
            return ENUM_FROM_INT.get(enumClass).invoke(value);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to resolve int enum value " + value + " for " + enumClass.getName(), e);
        }
    }

    private static final JsonCodec DISPATCH_CODEC = JsonCodec.builder().build();

    /**
     * Serializes a document value via the dispatch codec.
     */
    public static void serializeDocument(Object doc, WriterContext ctx) {
        if (doc == null) {
            ctx.ensureCapacity(4);
            ctx.pos = writeNull(ctx.buf, ctx.pos);
            return;
        }
        serializeViaDispatch((SerializableShape) doc, ctx);
    }

    /**
     * Deserializes a document value via the dispatch codec.
     */
    public static Object deserializeDocument(JsonReaderContext ctx) {
        // Find the end of the JSON value so we only pass the document bytes
        // to the dispatch codec and correctly advance the position.
        int valueEnd = JsonReadUtils.skipValue(ctx.buf, ctx.pos, ctx.end, ctx);
        byte[] valueBytes = java.util.Arrays.copyOfRange(ctx.buf, ctx.pos, valueEnd);
        var deser = DISPATCH_CODEC.createDeserializer(valueBytes);
        Object doc = deser.readDocument();
        deser.close();
        ctx.pos = valueEnd;
        return doc;
    }

    private static void serializeViaDispatch(SerializableShape shape, WriterContext ctx) {
        ByteBuffer result = DISPATCH_CODEC.serialize(shape);
        byte[] bytes = new byte[result.remaining()];
        result.get(bytes);
        ctx.ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, ctx.buf, ctx.pos, bytes.length);
        ctx.pos += bytes.length;
    }

    private static Object deserializeViaDispatch(JsonReaderContext ctx, Schema schema) {
        // Find the end of the JSON value at ctx.pos so we only pass the relevant
        // portion to the dispatch codec and correctly advance the position.
        int valueEnd = JsonReadUtils.skipValue(ctx.buf, ctx.pos, ctx.end, ctx);
        byte[] valueBytes = java.util.Arrays.copyOfRange(ctx.buf, ctx.pos, valueEnd);
        ShapeBuilder<?> builder = schema.shapeBuilder();
        Object result = DISPATCH_CODEC.deserializeShape(valueBytes, builder);
        ctx.pos = valueEnd;
        return result;
    }

    /**
     * Parses epoch seconds from a JSON number, handling fractional nanoseconds.
     * Stores the resulting Instant's epoch second and nano in the parse state.
     *
     * @return the parsed Instant
     */
    public static Instant parseEpochSeconds(byte[] buf, int pos, int end, JsonParseState state) {
        int start = pos;

        // Check for negative
        boolean negative = false;
        if (pos < end && buf[pos] == '-') {
            negative = true;
            pos++;
        }

        // Parse integer part
        long seconds = 0;
        while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
            seconds = seconds * 10 + (buf[pos] - '0');
            pos++;
        }
        if (negative) {
            seconds = -seconds;
        }

        int nano = 0;
        if (pos < end && buf[pos] == '.') {
            pos++; // skip '.'
            int fracStart = pos;
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
            int fracLen = pos - fracStart;
            // Parse up to 9 fractional digits
            for (int i = 0; i < 9; i++) {
                nano *= 10;
                if (i < fracLen) {
                    nano += buf[fracStart + i] - '0';
                }
            }
            if (negative && nano > 0) {
                // Adjust for Instant's contract: nanos are always non-negative
                seconds -= 1;
                nano = 1_000_000_000 - nano;
            }
        }

        state.parsedEndPos = pos;
        return Instant.ofEpochSecond(seconds, nano);
    }

    /**
     * Writes the JSON null literal to the buffer.
     *
     * @return new position after writing
     */
    public static int writeNull(byte[] buf, int pos) {
        buf[pos] = 'n';
        buf[pos + 1] = 'u';
        buf[pos + 2] = 'l';
        buf[pos + 3] = 'l';
        return pos + 4;
    }
}
