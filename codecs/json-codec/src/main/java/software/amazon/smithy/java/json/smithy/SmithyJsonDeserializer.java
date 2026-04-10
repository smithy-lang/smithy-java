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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * High-performance JSON deserializer that parses directly from a byte array.
 *
 * <p>Operates on raw bytes with position/end pointers — no token abstraction,
 * no symbol table, no intermediate String allocation for field names.
 * Uses FNV-1a hash-based field matching via {@link SmithyMemberLookup} with
 * speculative ordered matching for the common case.
 *
 * <p>Implements strict RFC 8259 compliance: validates all tokens, rejects
 * malformed input, enforces depth limits.
 */
final class SmithyJsonDeserializer implements ShapeDeserializer {

    private static final int MAX_DEPTH = 1000;
    private static final Base64.Decoder BASE64_DECODER = Base64.getMimeDecoder();

    private final byte[] buf;
    private int pos;
    private final int end;
    private final JsonSettings settings;
    private final boolean useJsonName;
    private int depth;

    // Mutable result fields — avoids allocating arrays on every parse call.
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
        return new BigInteger(numStr);
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
        return new BigDecimal(numStr);
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
        String base64 = readString(schema);
        try {
            return ByteBuffer.wrap(BASE64_DECODER.decode(base64));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Invalid base64 in blob value", e);
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        skipWhitespace();
        var format = settings.timestampResolver().resolve(schema);
        if (pos < end && buf[pos] == '"') {
            // String form
            String s = readStringValue();
            return format.readFromString(s, true);
        }
        if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9'))) {
            // Number form
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
            case 't' -> "Boolean value";
            case 'f' -> "Boolean value";
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
        skipWhitespace();
        expect('{');
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        skipWhitespace();

        // Empty object
        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return;
        }

        // Get the member lookup for this struct.
        // If schema is a member schema (e.g. ComplexStruct$nested), resolve to the target struct
        // schema to get the struct-level extension with member lookups.
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
        SmithyMemberLookup lookup = null;
        if (ext != null) {
            lookup = useJsonName ? ext.jsonNameLookup() : ext.memberNameLookup();
        }
        // Speculative state is local to this call (not on the shared lookup)
        int expectedNext = 0;

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos >= end) {
                    throw new SerializationException("Unterminated object");
                }
                if (buf[pos] == '}') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
            }
            first = false;

            skipWhitespace();

            // Parse field name and compute FNV-1a hash in a single pass.
            // This fuses what was previously two separate scans (scan for '"' + fnvHash).
            // Note: Smithy member names and jsonName trait values cannot contain backslashes
            // or characters requiring JSON escaping, so we don't need to handle escape
            // sequences in field names for member lookup purposes. Unknown escaped field
            // names will simply not match any member and be skipped via the unknown-field path.
            if (pos >= end || buf[pos] != '"') {
                throw new SerializationException(
                        "Expected field name, found: " + JsonReadUtils.describePos(buf, pos, end));
            }
            pos++; // skip opening quote
            int nameStart = pos;
            long nameHash = 0xcbf29ce484222325L; // FNV-1a offset basis
            while (pos < end && buf[pos] != '"') {
                if (buf[pos] == '\\') {
                    pos++; // skip escaped char (hash includes raw bytes)
                }
                nameHash ^= buf[pos] & 0xFF;
                nameHash *= 0x100000001b3L;
                pos++;
            }
            if (pos >= end) {
                throw new SerializationException("Unterminated field name");
            }
            int nameEnd = pos;
            pos++; // skip closing quote

            // Skip colon
            skipWhitespace();
            expect(':');
            skipWhitespace();

            // Look up member using pre-computed hash + stateless lookup
            Schema member = lookup != null
                    ? lookup.lookupWithHash(buf, nameStart, nameEnd, nameHash, expectedNext)
                    : null;
            if (member != null) {
                expectedNext = member.memberIndex() + 1;
            }

            if (member != null) {
                // Check for null value
                if (pos < end && buf[pos] == 'n'
                        && pos + 4 <= end
                        && buf[pos + 1] == 'u'
                        && buf[pos + 2] == 'l'
                        && buf[pos + 3] == 'l') {
                    pos += 4;
                    // null value — skip (don't call consumer)
                } else {
                    structMemberConsumer.accept(state, member, this);
                }
            } else {
                // Unknown field
                String fieldName = new String(buf, nameStart, nameEnd - nameStart, StandardCharsets.UTF_8);

                if (schema.type() == ShapeType.STRUCTURE) {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                } else if (fieldName.equals("__type")) {
                    // Ignore __type on unknown union members
                    skipValue();
                } else if (settings.forbidUnknownUnionMembers()) {
                    throw new SerializationException("Unknown member " + fieldName + " encountered");
                } else {
                    structMemberConsumer.unknownMember(state, fieldName);
                    skipValue();
                }
            }
        }
    }

    // ---- List deserialization ----

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        skipWhitespace();
        if (pos >= end || buf[pos] != '[') {
            throw new SerializationException(
                    "Expected a list, but found " + describeCurrentToken());
        }
        pos++; // skip '['
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        skipWhitespace();

        // Empty array
        if (pos < end && buf[pos] == ']') {
            pos++;
            depth--;
            return;
        }

        listMemberConsumer.accept(state, this);
        while (true) {
            skipWhitespace();
            if (pos < end && buf[pos] == ']') {
                pos++;
                depth--;
                return;
            }
            if (pos < end && buf[pos] == ',') {
                pos++;
                skipWhitespace();
                listMemberConsumer.accept(state, this);
            } else {
                throw new SerializationException(
                        "Expected end of list, but found " + describeCurrentToken());
            }
        }
    }

    // ---- Map deserialization ----

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        skipWhitespace();
        expect('{');
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }

        skipWhitespace();

        // Empty object
        if (pos < end && buf[pos] == '}') {
            pos++;
            depth--;
            return;
        }

        boolean first = true;
        while (true) {
            if (!first) {
                skipWhitespace();
                if (pos < end && buf[pos] == '}') {
                    pos++;
                    depth--;
                    return;
                }
                expect(',');
                skipWhitespace();
            }
            first = false;

            // Parse key
            String key = readStringValue();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            mapMemberConsumer.accept(state, key, this);
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
                    pos = JsonReadUtils.findNumberEnd(buf, pos, end);
                } else {
                    throw new SerializationException(
                            "Unexpected token: " + JsonReadUtils.describePos(buf, pos, end));
                }
            }
        }
    }

    private void skipString() {
        pos++; // skip opening '"'
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                pos++;
                return;
            }
            if (b == '\\') {
                pos++; // skip escape char
            }
            pos++;
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
