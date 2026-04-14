/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * High-performance JSON deserializer that parses directly from a byte array.
 *
 * <p>Implements strict RFC 8259 compliance: validates all tokens, rejects
 * malformed input, enforces depth limits.
 */
final class SmithyJsonDeserializer implements ShapeDeserializer {

    private static final int MAX_DEPTH = 1000;

    private final byte[] buf;
    private int pos;
    private final int end;
    private final JsonSettings settings;
    private final boolean useJsonName;
    private int depth;

    // Mutable result fields, avoids allocating arrays on every parse call.
    // Safe because the deserializer is single-threaded (one instance per operation).
    long parsedLong;
    int parsedEndPos;
    double parsedDouble;
    String parsedString;

    SmithyJsonDeserializer(byte[] buf, int pos, int end, JsonSettings settings) {
        this.buf = buf;
        this.pos = pos;
        this.end = end;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.depth = 0;
        // Position at the first non-whitespace token
        this.pos = JsonReadUtils.skipWhitespace(buf, pos, end);
    }

    @Override
    public void close() {
        // Verify no trailing non-whitespace content
        int p = JsonReadUtils.skipWhitespace(buf, pos, end);
        if (p < end) {
            throw new SerializationException(
                    "Unexpected JSON content: " + JsonReadUtils.describePos(buf, p, end));
        }
    }

    // ---- Primitive readers ----

    @Override
    public boolean readBoolean(Schema schema) {
        skipWhitespace();
        if (pos + 4 <= end && buf[pos] == 't') {
            if (buf[pos + 1] == 'r' && buf[pos + 2] == 'u' && buf[pos + 3] == 'e') {
                pos += 4;
                return true;
            }
            throw new SerializationException("Invalid token: expected 'true'");
        }
        if (pos + 5 <= end && buf[pos] == 'f') {
            if (buf[pos + 1] == 'a' && buf[pos + 2] == 'l' && buf[pos + 3] == 's' && buf[pos + 4] == 'e') {
                pos += 5;
                return false;
            }
            throw new SerializationException("Invalid token: expected 'false'");
        }
        throw new SerializationException(
                "Expected boolean, found: " + JsonReadUtils.describePos(buf, pos, end));
    }

    @Override
    public byte readByte(Schema schema) {
        skipWhitespace();
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Byte.MIN_VALUE || parsedLong > Byte.MAX_VALUE) {
            throw new SerializationException("Value out of byte range: " + parsedLong);
        }
        return (byte) parsedLong;
    }

    @Override
    public short readShort(Schema schema) {
        skipWhitespace();
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Short.MIN_VALUE || parsedLong > Short.MAX_VALUE) {
            throw new SerializationException("Value out of short range: " + parsedLong);
        }
        return (short) parsedLong;
    }

    @Override
    public int readInteger(Schema schema) {
        skipWhitespace();
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        if (parsedLong < Integer.MIN_VALUE || parsedLong > Integer.MAX_VALUE) {
            throw new SerializationException("Value out of integer range: " + parsedLong);
        }
        return (int) parsedLong;
    }

    @Override
    public long readLong(Schema schema) {
        skipWhitespace();
        JsonReadUtils.parseLong(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedLong;
    }

    @Override
    public float readFloat(Schema schema) {
        skipWhitespace();
        if (pos < end && buf[pos] == '"') {
            String s = readStringValue();
            return switch (s) {
                case "NaN" -> Float.NaN;
                case "Infinity" -> Float.POSITIVE_INFINITY;
                case "-Infinity" -> Float.NEGATIVE_INFINITY;
                default -> throw new SerializationException("Expected float, found string: \"" + s + "\"");
            };
        }
        JsonReadUtils.parseDouble(buf, pos, end, this);
        pos = parsedEndPos;
        return (float) parsedDouble;
    }

    @Override
    public double readDouble(Schema schema) {
        skipWhitespace();
        if (pos < end && buf[pos] == '"') {
            String s = readStringValue();
            return switch (s) {
                case "NaN" -> Double.NaN;
                case "Infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity" -> Double.NEGATIVE_INFINITY;
                default -> throw new SerializationException("Expected double, found string: \"" + s + "\"");
            };
        }
        JsonReadUtils.parseDouble(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedDouble;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        skipWhitespace();
        if (pos >= end || (buf[pos] != '-' && (buf[pos] < '0' || buf[pos] > '9'))) {
            throw new SerializationException(
                    "Expected number for BigInteger, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = pos;
        pos = JsonReadUtils.findNumberEnd(buf, pos, end);
        String numStr = new String(buf, start, pos - start, StandardCharsets.US_ASCII);
        try {
            return new BigInteger(numStr);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigInteger value: " + numStr, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        skipWhitespace();
        if (pos >= end || (buf[pos] != '-' && (buf[pos] < '0' || buf[pos] > '9'))) {
            throw new SerializationException(
                    "Expected number for BigDecimal, found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int start = pos;
        pos = JsonReadUtils.findNumberEnd(buf, pos, end);
        String numStr = new String(buf, start, pos - start, StandardCharsets.US_ASCII);
        try {
            return new BigDecimal(numStr);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigDecimal value: " + numStr, e);
        }
    }

    @Override
    public String readString(Schema schema) {
        skipWhitespace();
        return readStringValue();
    }

    private String readStringValue() {
        JsonReadUtils.parseString(buf, pos, end, this);
        pos = parsedEndPos;
        return parsedString;
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        skipWhitespace();
        byte[] decoded = JsonReadUtils.decodeBase64String(buf, pos, end, this);
        pos = parsedEndPos;
        return ByteBuffer.wrap(decoded);
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        skipWhitespace();
        var format = settings.timestampResolver().resolve(schema);
        if (format == TimestampFormatter.Prelude.EPOCH_SECONDS
                && pos < end
                && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9'))) {
            // Fast path for epoch-seconds: try integer parsing first.
            // Most epoch-seconds timestamps are whole numbers, so parseLong avoids
            // the expensive FastDoubleParser path entirely.
            int startPos = pos;
            JsonReadUtils.parseLong(buf, pos, end, this);
            int endPos = parsedEndPos;
            if (endPos < end && buf[endPos] == '.') {
                // Fractional epoch-seconds: parse with full nanosecond precision
                // instead of going through double (which truncates to ~15 significant digits).
                int fracPos = endPos + 1;
                int fracStart = fracPos;
                while (fracPos < end && buf[fracPos] >= '0' && buf[fracPos] <= '9') {
                    fracPos++;
                }
                int fracLen = fracPos - fracStart;
                if (fracLen > 0) {
                    int nano = 0;
                    for (int i = 0; i < 9; i++) {
                        nano *= 10;
                        if (i < fracLen) {
                            nano += buf[fracStart + i] - '0';
                        }
                    }
                    pos = fracPos;
                    long epochSecond = parsedLong;
                    boolean negative = buf[startPos] == '-';
                    if (negative && nano > 0) {
                        // -0.5 means parsedLong=0 but the value is -0.5 = Instant(-1, 500_000_000)
                        // -1.5 means parsedLong=-1 but the value is -1.5 = Instant(-2, 500_000_000)
                        epochSecond -= 1;
                        nano = 1_000_000_000 - nano;
                    }
                    try {
                        return Instant.ofEpochSecond(epochSecond, nano);
                    } catch (java.time.DateTimeException e) {
                        throw new SerializationException("Epoch seconds out of range: " + parsedLong, e);
                    }
                }
                // No digits after dot — fall through to double parsing
            } else if (endPos >= end || (buf[endPos] != 'e' && buf[endPos] != 'E')) {
                // Pure integer — no fractional part
                pos = endPos;
                try {
                    return Instant.ofEpochSecond(parsedLong);
                } catch (java.time.DateTimeException e) {
                    throw new SerializationException("Epoch seconds out of range: " + parsedLong, e);
                }
            }
            // Has exponent or unparseable fraction — fall through to double parsing
            JsonReadUtils.parseDouble(buf, pos, end, this);
            pos = parsedEndPos;
            return format.readFromNumber(parsedDouble);
        }
        if (pos < end && buf[pos] == '"') {
            // Fast path: parse ISO-8601 and HTTP-date directly from bytes,
            // bypassing String allocation and DateTimeFormatter.
            if (format == TimestampFormatter.Prelude.DATE_TIME) {
                Instant result = JsonReadUtils.parseIso8601(buf, pos, end, this);
                if (result != null) {
                    pos = parsedEndPos;
                    return result;
                }
            } else if (format == TimestampFormatter.Prelude.HTTP_DATE) {
                Instant result = JsonReadUtils.parseHttpDate(buf, pos, end, this);
                if (result != null) {
                    pos = parsedEndPos;
                    return result;
                }
            }
            // Fallback: parse as String and use DateTimeFormatter
            String s = readStringValue();
            try {
                return format.readFromString(s, true);
            } catch (java.time.DateTimeException e) {
                throw new SerializationException("Invalid timestamp: " + s, e);
            }
        }
        if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9'))) {
            JsonReadUtils.parseDouble(buf, pos, end, this);
            pos = parsedEndPos;
            return format.readFromNumber(parsedDouble);
        }
        throw new SerializationException(
                "Expected a timestamp, but found " + describeCurrentToken());
    }

    private String describeCurrentToken() {
        if (pos >= end) {
            return "end of input";
        }
        return switch (buf[pos]) {
            case 't', 'f' -> "Boolean value";
            case 'n' -> "Null value";
            case '[' -> "Array value";
            case '{' -> "Object value";
            case '"' -> "String value";
            default -> JsonReadUtils.describePos(buf, pos, end);
        };
    }

    // ---- Struct deserialization ----

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        // Localize hot fields to registers. The JIT cannot promote instance fields across
        // virtual calls (the structMemberConsumer callback), so keeping buf/end/pos as locals
        // between callbacks eliminates ~6 memory loads/stores per field iteration.
        final byte[] localBuf = this.buf;
        final int localEnd = this.end;
        int p = JsonReadUtils.skipWhitespace(localBuf, this.pos, localEnd);

        if (p >= localEnd || localBuf[p] != '{') {
            this.pos = p;
            throw new SerializationException(
                    "Expected '{', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

        // Empty object
        if (p < localEnd && localBuf[p] == '}') {
            this.pos = p + 1;
            depth--;
            return;
        }

        // Get the member lookup for this struct.
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
        SmithyMemberLookup lookup = null;
        if (ext != null) {
            lookup = useJsonName ? ext.jsonNameLookup() : ext.memberNameLookup();
        }
        int expectedNext = 0;

        boolean first = true;
        while (true) {
            if (!first) {
                p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);
                if (p >= localEnd) {
                    this.pos = p;
                    throw new SerializationException("Unterminated object");
                }
                if (localBuf[p] == '}') {
                    this.pos = p + 1;
                    depth--;
                    return;
                }
                if (p >= localEnd || localBuf[p] != ',') {
                    this.pos = p;
                    throw new SerializationException(
                            "Expected ',', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
                }
                p++;
            }
            first = false;

            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

            // Parse field name
            if (p >= localEnd || localBuf[p] != '"') {
                this.pos = p;
                throw new SerializationException(
                        "Expected field name, found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
            }
            p++; // skip opening quote

            // Fused speculative path: check expected field name directly at current
            // position without scanning for the closing quote byte-by-byte. If the
            // expected name matches and the byte after it is '"', we skip the scan
            // entirely. This eliminates the per-byte scan loop on the common path
            // where JSON fields arrive in schema definition order.
            Schema member = null;
            if (lookup != null && expectedNext >= 0 && expectedNext < lookup.orderedNameBytes.length) {
                byte[] expected = lookup.orderedNameBytes[expectedNext];
                int expLen = expected.length;
                if (p + expLen < localEnd
                        && localBuf[p + expLen] == '"'
                        && Arrays.equals(localBuf, p, p + expLen, expected, 0, expLen)) {
                    member = lookup.orderedSchemas[expectedNext];
                    expectedNext = member.memberIndex() + 1;
                    p += expLen + 1; // skip name + closing quote
                }
            }

            // Slow path: scan for closing quote and look up member by hash
            int nameStart = -1, nameEnd = -1;
            if (member == null) {
                nameStart = p;
                while (p < localEnd && localBuf[p] != '"') {
                    if (localBuf[p] == '\\') {
                        p++; // skip escaped char
                    }
                    p++;
                }
                if (p >= localEnd) {
                    this.pos = p;
                    throw new SerializationException("Unterminated field name");
                }
                nameEnd = p;
                p++; // skip closing quote
            }

            // Skip colon
            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);
            if (p >= localEnd || localBuf[p] != ':') {
                this.pos = p;
                throw new SerializationException(
                        "Expected ':', found: " + JsonReadUtils.describePos(localBuf, p, localEnd));
            }
            p++;
            p = JsonReadUtils.skipWhitespace(localBuf, p, localEnd);

            // Slow path member lookup (only when speculative check missed)
            if (member == null) {
                member = lookup != null
                        ? lookup.lookup(localBuf, nameStart, nameEnd, expectedNext)
                        : null;
                if (member != null) {
                    expectedNext = member.memberIndex() + 1;
                }
            }

            if (member != null) {
                // Check for null value
                if (p < localEnd && localBuf[p] == 'n'
                        && p + 4 <= localEnd
                        && localBuf[p + 1] == 'u'
                        && localBuf[p + 2] == 'l'
                        && localBuf[p + 3] == 'l') {
                    p += 4;
                } else {
                    // Write pos back before callback, reload after
                    this.pos = p;
                    structMemberConsumer.accept(state, member, this);
                    p = this.pos;
                }
            } else {
                // Unknown field — validate field name bytes per RFC 8259
                // (control chars, escape sequences). This is the cold path only.
                validateSkippedString(localBuf, nameStart, nameEnd);
                this.pos = p;
                String fieldName = new String(localBuf, nameStart, nameEnd - nameStart, StandardCharsets.UTF_8);

                if (schema.type() == ShapeType.STRUCTURE) {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                } else if (fieldName.equals("__type")) {
                    skipValue();
                } else if (settings.forbidUnknownUnionMembers()) {
                    throw new SerializationException("Unknown member " + fieldName + " encountered");
                } else {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                }
                p = this.pos;
            }
        }
    }

    // ---- List deserialization ----

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        final byte[] buf = this.buf;
        final int end = this.end;
        int p = JsonReadUtils.skipWhitespace(buf, this.pos, end);

        if (p >= end || buf[p] != '[') {
            this.pos = p;
            throw new SerializationException(
                    "Expected a list, but found " + describeCurrentToken());
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(buf, p, end);

        // Empty array
        if (p < end && buf[p] == ']') {
            this.pos = p + 1;
            depth--;
            return;
        }

        this.pos = p;
        listMemberConsumer.accept(state, this);
        p = this.pos;
        while (true) {
            p = JsonReadUtils.skipWhitespace(buf, p, end);
            if (p < end && buf[p] == ']') {
                this.pos = p + 1;
                depth--;
                return;
            }
            if (p < end && buf[p] == ',') {
                p++;
                p = JsonReadUtils.skipWhitespace(buf, p, end);
                this.pos = p;
                listMemberConsumer.accept(state, this);
                p = this.pos;
            } else {
                this.pos = p;
                throw new SerializationException(
                        "Expected end of list, but found " + describeCurrentToken());
            }
        }
    }

    // ---- Map deserialization ----

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        final byte[] buf = this.buf;
        final int end = this.end;
        int p = JsonReadUtils.skipWhitespace(buf, this.pos, end);

        if (p >= end || buf[p] != '{') {
            this.pos = p;
            throw new SerializationException(
                    "Expected '{', found: " + JsonReadUtils.describePos(buf, p, end));
        }
        p++;
        depth++;
        if (depth > MAX_DEPTH) {
            this.pos = p;
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        p = JsonReadUtils.skipWhitespace(buf, p, end);

        // Empty object
        if (p < end && buf[p] == '}') {
            this.pos = p + 1;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                p = JsonReadUtils.skipWhitespace(buf, p, end);
                if (p < end && buf[p] == '}') {
                    this.pos = p + 1;
                    depth--;
                    return;
                }
                if (p >= end || buf[p] != ',') {
                    this.pos = p;
                    throw new SerializationException(
                            "Expected ',', found: " + JsonReadUtils.describePos(buf, p, end));
                }
                p++;
                p = JsonReadUtils.skipWhitespace(buf, p, end);
            }
            first = false;

            // Parse key — need to write pos back for readStringValue
            this.pos = p;
            String key = readStringValue();
            p = JsonReadUtils.skipWhitespace(buf, this.pos, end);
            if (p >= end || buf[p] != ':') {
                this.pos = p;
                throw new SerializationException(
                        "Expected ':', found: " + JsonReadUtils.describePos(buf, p, end));
            }
            p++;
            p = JsonReadUtils.skipWhitespace(buf, p, end);

            this.pos = p;
            mapMemberConsumer.accept(state, key, this);
            p = this.pos;
        }
    }

    // ---- Document deserialization ----

    @Override
    public Document readDocument() {
        skipWhitespace();
        if (pos >= end) {
            throw new SerializationException("Expected a JSON value");
        }

        return switch (buf[pos]) {
            case 'n' -> {
                expectLiteral("null");
                yield null;
            }
            case 't' -> {
                expectLiteral("true");
                yield JsonDocuments.of(true, settings);
            }
            case 'f' -> {
                expectLiteral("false");
                yield JsonDocuments.of(false, settings);
            }
            case '"' -> JsonDocuments.of(readStringValue(), settings);
            case '[' -> {
                pos++; // skip '['
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded");
                }
                List<Document> values = new ArrayList<>();
                skipWhitespace();
                if (pos < end && buf[pos] != ']') {
                    values.add(readDocument());
                    skipWhitespace();
                    while (pos < end && buf[pos] == ',') {
                        pos++;
                        values.add(readDocument());
                        skipWhitespace();
                    }
                }
                expect(']');
                depth--;
                yield JsonDocuments.of(values, settings);
            }
            case '{' -> {
                pos++; // skip '{'
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded");
                }
                Map<String, Document> values = new LinkedHashMap<>();
                skipWhitespace();
                if (pos < end && buf[pos] != '}') {
                    String key = readStringValue();
                    skipWhitespace();
                    expect(':');
                    values.put(key, readDocument());
                    skipWhitespace();
                    while (pos < end && buf[pos] == ',') {
                        pos++;
                        skipWhitespace();
                        key = readStringValue();
                        skipWhitespace();
                        expect(':');
                        values.put(key, readDocument());
                        skipWhitespace();
                    }
                }
                expect('}');
                depth--;
                yield JsonDocuments.of(values, settings);
            }
            default -> {
                // Must be a number
                if (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')) {
                    yield parseDocumentNumber();
                }
                throw new SerializationException(
                        "Unexpected token: " + JsonReadUtils.describePos(buf, pos, end));
            }
        };
    }

    /**
     * Parses a number in document context with strict RFC 8259 validation.
     * Determines the appropriate Number type (Integer, Long, BigInteger, or Double).
     */
    private Document parseDocumentNumber() {
        // Use parseDouble to strictly validate the number grammar
        JsonReadUtils.parseDouble(buf, pos, end, this);
        int newPos = parsedEndPos;

        // Check if the number has fractional/exponent parts
        boolean isFloat = false;
        for (int i = pos; i < newPos; i++) {
            if (buf[i] == '.' || buf[i] == 'e' || buf[i] == 'E') {
                isFloat = true;
                break;
            }
        }

        String numStr = new String(buf, pos, newPos - pos, StandardCharsets.US_ASCII);
        pos = newPos;

        Number number;
        if (isFloat) {
            number = parsedDouble;
        } else {
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
        return JsonDocuments.of(number, settings);
    }

    // ---- Null handling ----

    @Override
    public boolean isNull() {
        skipWhitespace();
        return pos < end && buf[pos] == 'n';
    }

    @Override
    public <T> T readNull() {
        skipWhitespace();
        expectLiteral("null");
        return null;
    }

    // ---- Validation for skipped content ----

    /**
     * Validates string bytes (between quotes) for RFC 8259 compliance without building a String.
     * Checks for unescaped control characters and valid escape sequences.
     */
    private static void validateSkippedString(byte[] buf, int start, int end) {
        int p = start;
        while (p < end) {
            byte b = buf[p];
            if (b == '\\') {
                p++;
                if (p >= end) {
                    throw new SerializationException("Unterminated escape sequence");
                }
                byte esc = buf[p];
                switch (esc) {
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    }
                    case 'u' -> {
                        if (p + 4 >= end) {
                            throw new SerializationException("Unterminated \\u escape");
                        }
                        p += 4;
                    }
                    default -> throw new SerializationException(
                            "Invalid escape sequence: \\" + (char) esc);
                }
            } else if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character (0x" + Integer.toHexString(b & 0xFF) + ")");
            }
            p++;
        }
    }

    // ---- Value skipping for unknown fields ----

    private void skipValue() {
        skipWhitespace();
        if (pos >= end) {
            throw new SerializationException("Unexpected end of input");
        }

        switch (buf[pos]) {
            case '"' -> skipString();
            case '{' -> skipObject();
            case '[' -> skipArray();
            case 't' -> {
                expectLiteral("true");
            }
            case 'f' -> {
                expectLiteral("false");
            }
            case 'n' -> {
                expectLiteral("null");
            }
            default -> {
                if (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')) {
                    // Use parseDouble for strict RFC 8259 number validation
                    // (rejects leading zeros, double exponents, etc.)
                    JsonReadUtils.parseDouble(buf, pos, end, this);
                    pos = parsedEndPos;
                } else {
                    throw new SerializationException(
                            "Unexpected token: " + JsonReadUtils.describePos(buf, pos, end));
                }
            }
        }
    }

    private void skipString() {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException(
                    "Expected '\"', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        int p = pos + 1; // skip opening '"'
        final byte[] buf = this.buf;
        final int end = this.end;
        while (p < end) {
            byte b = buf[p];
            if (b == '"') {
                pos = p + 1;
                return;
            }
            if (b == '\\') {
                p++;
                if (p >= end) {
                    break;
                }
                byte esc = buf[p];
                switch (esc) {
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    }
                    case 'u' -> {
                        // \\uXXXX — skip 4 hex digits
                        if (p + 4 >= end) {
                            throw new SerializationException("Unterminated \\u escape");
                        }
                        p += 4;
                    }
                    default -> throw new SerializationException(
                            "Invalid escape sequence: \\" + (char) esc);
                }
            } else if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character (0x" + Integer.toHexString(b & 0xFF) + ")");
            }
            p++;
        }
        throw new SerializationException("Unterminated string");
    }

    private void skipObject() {
        pos++; // skip '{'
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded");
        }
        skipWhitespace();

        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos >= end) {
                    throw new SerializationException("Unexpected end of input in object");
                }
                if (buf[pos] == '}') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
                skipWhitespace();
            }
            first = false;
            skipString(); // skip key
            skipWhitespace();
            expect(':');
            skipValue(); // skip value
        }
    }

    private void skipArray() {
        pos++; // skip '['
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded");
        }
        skipWhitespace();

        if (pos < end && buf[pos] == ']') {
            pos++;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos >= end) {
                    throw new SerializationException("Unexpected end of input in array");
                }
                if (buf[pos] == ']') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
                skipWhitespace();
            }
            first = false;
            skipValue();
        }
    }

    // ---- Utility methods ----

    private void skipWhitespace() {
        pos = JsonReadUtils.skipWhitespace(buf, pos, end);
    }

    private void expect(char c) {
        if (pos >= end || buf[pos] != c) {
            throw new SerializationException(
                    "Expected '" + c + "', found: " + JsonReadUtils.describePos(buf, pos, end));
        }
        pos++;
    }

    private void expectLiteral(String literal) {
        int len = literal.length();
        if (pos + len > end) {
            throw new SerializationException("Unexpected end of input, expected '" + literal + "'");
        }
        for (int i = 0; i < len; i++) {
            if (buf[pos + i] != literal.charAt(i)) {
                throw new SerializationException("Expected '" + literal + "', found: "
                        + new String(buf, pos, Math.min(len, end - pos), StandardCharsets.US_ASCII));
            }
        }
        pos += len;
    }
}
