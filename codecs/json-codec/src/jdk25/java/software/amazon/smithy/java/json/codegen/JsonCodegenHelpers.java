/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codegen.rt.CodegenHelpers;
import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;
import software.amazon.smithy.java.codegen.rt.WriterContext;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonSettings;
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
        SerializableStruct struct = (SerializableStruct) obj;
        GeneratedStructSerializer ser = ctx.registry.getSerializer(struct.schema(), obj.getClass());
        if (ser != null) {
            ser.serialize(struct, ctx);
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

    private static final JsonCodec DISPATCH_CODEC = JsonCodec.builder().build();

    public static void serializeDocument(Object doc, WriterContext ctx) {
        if (doc == null) {
            ctx.ensureCapacity(4);
            ctx.pos = writeNull(ctx.buf, ctx.pos);
            return;
        }
        try (var serializer = DISPATCH_CODEC.createSerializer(new WriterContextOutputStream(ctx))) {
            ((SerializableShape) doc).serialize(serializer);
        }
    }

    private static final JsonSettings DOC_SETTINGS = DISPATCH_CODEC.settings();

    public static Object deserializeDocument(JsonReaderContext ctx) {
        return readDocument(ctx.buf, ctx, ctx.jsonSettings != null ? ctx.jsonSettings : DOC_SETTINGS);
    }

    private static Document readDocument(byte[] buf, JsonReaderContext ctx, JsonSettings settings) {
        int pos = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
        ctx.pos = pos;
        if (pos >= ctx.end) {
            return null;
        }
        return switch (buf[pos]) {
            case 'n' -> {
                expectLiteral(buf, pos, ctx.end, "null");
                ctx.pos = pos + 4;
                yield null;
            }
            case 't' -> {
                expectLiteral(buf, pos, ctx.end, "true");
                ctx.pos = pos + 4;
                yield JsonDocuments.of(true, settings);
            }
            case 'f' -> {
                expectLiteral(buf, pos, ctx.end, "false");
                ctx.pos = pos + 5;
                yield JsonDocuments.of(false, settings);
            }
            case '"' -> {
                JsonReadUtils.parseString(buf, pos, ctx.end, ctx);
                String s = ctx.parsedString;
                ctx.pos = ctx.parsedEndPos;
                yield JsonDocuments.of(s, settings);
            }
            case '[' -> {
                ctx.pos = pos + 1;
                List<Document> values = new ArrayList<>();
                int p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                ctx.pos = p;
                if (p < ctx.end && buf[p] != ']') {
                    values.add(readDocument(buf, ctx, settings));
                    p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                    ctx.pos = p;
                    while (p < ctx.end && buf[p] == ',') {
                        ctx.pos = p + 1;
                        values.add(readDocument(buf, ctx, settings));
                        p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                        ctx.pos = p;
                    }
                }
                ctx.pos = p + 1; // skip ']'
                yield JsonDocuments.of(values, settings);
            }
            case '{' -> {
                ctx.pos = pos + 1;
                Map<String, Document> values = new LinkedHashMap<>();
                int p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                ctx.pos = p;
                if (p < ctx.end && buf[p] != '}') {
                    JsonReadUtils.parseString(buf, p, ctx.end, ctx);
                    String key = ctx.parsedString;
                    ctx.pos = ctx.parsedEndPos;
                    p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                    ctx.pos = p + 1; // skip ':'
                    values.put(key, readDocument(buf, ctx, settings));
                    p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                    ctx.pos = p;
                    while (p < ctx.end && buf[p] == ',') {
                        ctx.pos = p + 1;
                        p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                        ctx.pos = p;
                        JsonReadUtils.parseString(buf, p, ctx.end, ctx);
                        key = ctx.parsedString;
                        ctx.pos = ctx.parsedEndPos;
                        p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                        ctx.pos = p + 1; // skip ':'
                        values.put(key, readDocument(buf, ctx, settings));
                        p = JsonReadUtils.skipWhitespace(buf, ctx.pos, ctx.end);
                        ctx.pos = p;
                    }
                }
                ctx.pos = p + 1; // skip '}'
                yield JsonDocuments.of(values, settings);
            }
            default -> {
                // Number
                JsonReadUtils.parseDouble(buf, pos, ctx.end, ctx);
                int newPos = ctx.parsedEndPos;
                boolean isFloat = false;
                for (int i = pos; i < newPos; i++) {
                    if (buf[i] == '.' || buf[i] == 'e' || buf[i] == 'E') {
                        isFloat = true;
                        break;
                    }
                }
                ctx.pos = newPos;
                Number number;
                if (isFloat) {
                    number = ctx.parsedDouble;
                } else {
                    String numStr = new String(buf, pos, newPos - pos, StandardCharsets.US_ASCII);
                    try {
                        long lv = Long.parseLong(numStr);
                        if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                            number = (int) lv;
                        } else {
                            number = lv;
                        }
                    } catch (NumberFormatException e) {
                        number = new BigInteger(numStr);
                    }
                }
                yield JsonDocuments.of(number, settings);
            }
        };
    }

    private static void expectLiteral(byte[] buf, int pos, int end, String literal) {
        int len = literal.length();
        if (pos + len > end) {
            throw new SerializationException("Unexpected end of input, expected '" + literal + "'");
        }
        for (int i = 0; i < len; i++) {
            if (buf[pos + i] != literal.charAt(i)) {
                throw new SerializationException("Expected '" + literal + "' at position " + pos);
            }
        }
    }

    private static void serializeViaDispatch(SerializableShape shape, WriterContext ctx) {
        try (var serializer = DISPATCH_CODEC.createSerializer(new WriterContextOutputStream(ctx))) {
            shape.serialize(serializer);
        }
    }

    private static Object deserializeViaDispatch(JsonReaderContext ctx, Schema schema) {
        int valueEnd = JsonReadUtils.skipValue(ctx.buf, ctx.pos, ctx.end, ctx);
        byte[] valueBytes = java.util.Arrays.copyOfRange(ctx.buf, ctx.pos, valueEnd);
        ShapeBuilder<?> builder = schema.shapeBuilder();
        Object result = DISPATCH_CODEC.deserializeShape(valueBytes, builder);
        ctx.pos = valueEnd;
        return result;
    }

    public static void parseFloat(byte[] buf, int pos, int end, JsonParseState state) {
        if (pos < end && buf[pos] == '"') {
            JsonReadUtils.parseString(buf, pos, end, state);
            state.parsedDouble = Float.parseFloat(state.parsedString);
            return;
        }
        JsonReadUtils.parseDouble(buf, pos, end, state);
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

    /**
     * Fast-path quoted string write that takes buf/pos directly, avoiding ctx field round-trips.
     * Returns new pos on success (always positive). If the buffer needs to grow or the string
     * needs escaping, falls back to ctx-based slow path and returns {@code Integer.MIN_VALUE}.
     * Caller must reload buf and pos from ctx when the return is {@code Integer.MIN_VALUE}.
     */
    public static int writeQuotedStringFast(byte[] buf, int pos, String value, WriterContext ctx) {
        int len = value.length();
        if (pos + len + 2 > buf.length) {
            ctx.pos = pos;
            writeQuotedStringFused(ctx, value);
            return Integer.MIN_VALUE;
        }
        int startPos = pos;
        buf[pos++] = '"';
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c >= 0x80 || c < 0x20 || c == '"' || c == '\\') {
                ctx.pos = startPos;
                ctx.ensureCapacity(len * 6 + 2);
                ctx.pos = JsonWriteUtils.writeQuotedString(ctx.buf, ctx.pos, value);
                return Integer.MIN_VALUE;
            }
            buf[pos++] = (byte) c;
        }
        buf[pos++] = '"';
        return pos;
    }

    private static final class WriterContextOutputStream extends OutputStream {
        private final WriterContext ctx;

        WriterContextOutputStream(WriterContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void write(int b) {
            ctx.ensureCapacity(1);
            ctx.buf[ctx.pos++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            ctx.ensureCapacity(len);
            System.arraycopy(b, off, ctx.buf, ctx.pos, len);
            ctx.pos += len;
        }
    }
}
