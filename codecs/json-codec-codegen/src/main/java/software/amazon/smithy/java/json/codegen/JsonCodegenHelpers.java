/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.codegen.rt.CodegenHelpers;
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
import software.amazon.smithy.java.json.smithy.JsonWriteUtils;

/**
 * Static runtime helpers called by generated JSON serializer/deserializer code.
 *
 * <p>These methods handle complex cases that are not worth inlining into every generated class,
 * such as nested struct dispatch and document handling.
 */
public final class JsonCodegenHelpers {

    private JsonCodegenHelpers() {}

    public static void serializeNestedStruct(Object obj, WriterContext ctx) {
        if (obj == null) {
            ctx.ensureCapacity(4);
            ctx.pos = writeNull(ctx.buf, ctx.pos);
            return;
        }
        if (obj instanceof SerializableStruct struct) {
            GeneratedStructSerializer ser = ctx.registry.getSerializer(struct.schema(), obj.getClass());
            if (ser != null) {
                ser.serialize(obj, ctx);
                return;
            }
        }
        serializeViaDispatch((SerializableShape) obj, ctx);
    }

    public static void serializeNestedStructDirect(
            Object obj,
            WriterContext ctx,
            GeneratedStructSerializer[] cached,
            Class<?> structClass
    ) {
        if (obj == null) {
            ctx.ensureCapacity(4);
            ctx.pos = writeNull(ctx.buf, ctx.pos);
            return;
        }
        GeneratedStructSerializer ser = cached[0];
        if (ser == null) {
            if (obj instanceof SerializableStruct struct) {
                ser = ctx.registry.getSerializer(struct.schema(), structClass);
                if (ser != null) {
                    cached[0] = ser;
                }
            }
        }
        if (ser != null) {
            ser.serialize(obj, ctx);
        } else {
            serializeViaDispatch((SerializableShape) obj, ctx);
        }
    }

    public static Object deserializeNestedStruct(JsonReaderContext ctx, Class<?> structClass) {
        Schema schema = CodegenHelpers.schemaFor(structClass);
        GeneratedStructDeserializer de = ctx.registry.getDeserializer(schema, structClass);
        if (de != null) {
            ShapeBuilder<?> builder = schema.shapeBuilder();
            return de.deserialize(ctx, builder);
        }
        return deserializeViaDispatch(ctx, schema);
    }

    public static Object deserializeNestedStructDirect(
            JsonReaderContext ctx,
            Object[] cached,
            Class<?> structClass
    ) {
        GeneratedStructDeserializer de = (GeneratedStructDeserializer) cached[0];
        Schema schema = (Schema) cached[1];
        if (de == null) {
            schema = CodegenHelpers.schemaFor(structClass);
            de = ctx.registry.getDeserializer(schema, structClass);
            if (de != null) {
                cached[0] = de;
                cached[1] = schema;
            } else {
                return deserializeViaDispatch(ctx, schema);
            }
        }
        ShapeBuilder<?> builder = schema.shapeBuilder();
        return de.deserialize(ctx, builder);
    }

    private static final JsonCodec DISPATCH_CODEC = JsonCodec.builder().build();

    public static void serializeDocument(Object doc, WriterContext ctx) {
        if (doc == null) {
            ctx.ensureCapacity(4);
            ctx.pos = writeNull(ctx.buf, ctx.pos);
            return;
        }
        serializeViaDispatch((SerializableShape) doc, ctx);
    }

    public static Object deserializeDocument(JsonReaderContext ctx) {
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
        int valueEnd = JsonReadUtils.skipValue(ctx.buf, ctx.pos, ctx.end, ctx);
        byte[] valueBytes = java.util.Arrays.copyOfRange(ctx.buf, ctx.pos, valueEnd);
        ShapeBuilder<?> builder = schema.shapeBuilder();
        Object result = DISPATCH_CODEC.deserializeShape(valueBytes, builder);
        ctx.pos = valueEnd;
        return result;
    }

    public static Instant parseEpochSeconds(byte[] buf, int pos, int end, JsonParseState state) {
        int start = pos;

        boolean negative = false;
        if (pos < end && buf[pos] == '-') {
            negative = true;
            pos++;
        }

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
            pos++;
            int fracStart = pos;
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
            int fracLen = pos - fracStart;
            for (int i = 0; i < 9; i++) {
                nano *= 10;
                if (i < fracLen) {
                    nano += buf[fracStart + i] - '0';
                }
            }
            if (negative && nano > 0) {
                seconds -= 1;
                nano = 1_000_000_000 - nano;
            }
        }

        state.parsedEndPos = pos;
        return Instant.ofEpochSecond(seconds, nano);
    }

    public static int writeNull(byte[] buf, int pos) {
        buf[pos] = 'n';
        buf[pos + 1] = 'u';
        buf[pos + 2] = 'l';
        buf[pos + 3] = 'l';
        return pos + 4;
    }

    public static void writeQuotedStringFused(WriterContext ctx, String value) {
        int len = value.length();
        ctx.ensureCapacity(len + 2);
        byte[] buf = ctx.buf;
        int pos = ctx.pos;
        int startPos = pos;
        buf[pos++] = '"';

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c >= 0x80 || c < 0x20 || c == '"' || c == '\\') {
                ctx.pos = startPos;
                ctx.ensureCapacity(len * 6 + 2);
                ctx.pos = JsonWriteUtils.writeQuotedString(ctx.buf, ctx.pos, value);
                return;
            }
            buf[pos++] = (byte) c;
        }

        buf[pos++] = '"';
        ctx.pos = pos;
    }
}
