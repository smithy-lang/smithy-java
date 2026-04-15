/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborReadUtil.readByteString;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.cbor.CborParser.Token;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;

final class CborDeserializer implements ShapeDeserializer {

    private final CborParser parser;
    private final CborSettings settings;
    private final byte[] payload;

    CborDeserializer(byte[] payload, CborSettings settings) {
        this.parser = new CborParser(payload);
        this.settings = settings;
        this.payload = payload;
        parser.advance();
    }

    CborDeserializer(ByteBuffer byteBuffer, CborSettings settings) {
        this.settings = settings;
        if (byteBuffer.hasArray()) {
            byte[] payload = byteBuffer.array();
            this.payload = payload;
            int start = byteBuffer.arrayOffset() + byteBuffer.position();
            this.parser = new CborParser(
                    payload,
                    start,
                    start + byteBuffer.remaining());
        } else {
            int pos = byteBuffer.position();
            this.payload = new byte[byteBuffer.remaining()];
            byteBuffer.get(this.payload);
            this.parser = new CborParser(this.payload);
            byteBuffer.position(pos);
        }
        parser.advance();
    }

    @Override
    public void close() {
        if (parser.currentToken() != Token.FINISHED) {
            throw new SerializationException("Unexpected CBOR content at end of object");
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        byte token = parser.currentToken();
        if (token == Token.TRUE) {
            return true;
        } else if (token == Token.FALSE) {
            return false;
        }
        throw badType("boolean", token);
    }

    private static SerializationException badType(String type, byte token) {
        return new SerializationException("Can't read " + Token.name(token) + " as a " + type);
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        byte token = parser.currentToken();
        if (token == Token.BYTE_STRING) {
            int pos = parser.getPosition();
            int len = parser.getItemLength();
            ByteBuffer buffer;
            if (CborParser.isIndefinite(len)) {
                buffer = ByteBuffer.wrap(readByteString(payload, pos, len));
            } else {
                buffer = ByteBuffer.wrap(payload, pos, len).slice();
            }
            return buffer;
        }
        throw badType("blob", token);
    }

    @Override
    public byte readByte(Schema schema) {
        return (byte) readLong("byte", parser.currentToken());
    }

    @Override
    public short readShort(Schema schema) {
        return (short) readLong("short", parser.currentToken());
    }

    @Override
    public int readInteger(Schema schema) {
        return (int) readLong("integer", parser.currentToken());
    }

    @Override
    public long readLong(Schema schema) {
        return readLong("long", parser.currentToken());
    }

    private long readLong(String type, byte token) {
        int off = parser.getPosition();
        int len = parser.getItemLength();
        if (token > Token.NEG_INT)
            throw badType(type, token);
        long val = CborReadUtil.readLong(payload, token, off, len);
        if (len < 8) {
            return val;
        }
        if (token == Token.POS_INT) {
            return val < 0 ? Long.MAX_VALUE : val;
        } else {
            return val < 0 ? val : Long.MIN_VALUE;
        }
    }

    @Override
    public float readFloat(Schema schema) {
        return (float) readDouble("float", parser.currentToken());
    }

    @Override
    public double readDouble(Schema schema) {
        return readDouble("double", parser.currentToken());
    }

    private double readDouble(String type, byte token) {
        if (token != Token.FLOAT) {
            throw badType(type, token);
        }
        return readDouble(token);
    }

    private double readDouble(byte token) {

        int pos = parser.getPosition();
        int len = parser.getItemLength();
        long fp = CborReadUtil.readLong(payload, token, pos, len);
        // ordered by how likely it is we'll encounter each case
        if (len == 8) { // double
            return Double.longBitsToDouble(fp);
        } else if (len == 4) { // float
            return Float.intBitsToFloat((int) fp);
        } else { // b == 2  - half-precision float
            return float16((int) fp);
        }
    }

    // https://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java/6162687
    private static float float16(int hbits) {
        int mant = hbits & 0x03ff; // 10 bits mantissa
        int exp = hbits & 0x7c00; // 5 bits exponent
        if (exp == 0x7c00) { // NaN/Inf
            exp = 0x3fc00; // -> NaN/Inf
        } else if (exp != 0) { // normalized value
            exp += 0x1c000; // exp - 15 + 127
            if (mant == 0 && exp > 0x1c400) // smooth transition
                return Float.intBitsToFloat((hbits & 0x8000) << 16 | exp << 13);
        } else if (mant != 0) { // && exp==0 -> subnormal
            exp = 0x1c400; // make it normal
            do {
                mant <<= 1; // mantissa * 2
                exp -= 0x400; // decrease exp by 1
            } while ((mant & 0x400) == 0); // while not normal
            mant &= 0x3ff; // discard subnormal bit
        } // else +/-0 -> +/-0
        return Float.intBitsToFloat(
                // combine all parts
                (hbits & 0x8000) << 16 // sign  << ( 31 - 15 )
                        | (exp | mant) << 13 // value << ( 23 - 10 )
        );
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        byte token = parser.currentToken();
        int tmp = token & 0b11110;
        if (tmp != Token.POS_INT && tmp != Token.POS_BIGINT) {
            throw badType("biginteger", token);
        }
        return CborReadUtil.readBigInteger(payload, token, parser.getPosition(), parser.getItemLength());
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        byte token = parser.currentToken();
        if (token == Token.BIG_DECIMAL) {
            return CborReadUtil.readBigDecimal(payload, parser.getPosition());
        } else if (token == Token.FLOAT) {
            return BigDecimal.valueOf(readDouble(token));
        } else if (token <= Token.NEG_INT) {
            return BigDecimal
                    .valueOf(CborReadUtil.readLong(payload, token, parser.getPosition(), parser.getItemLength()));
        }
        throw badType("bigdecimal", token);
    }

    @Override
    public String readString(Schema schema) {
        byte token = parser.currentToken();
        if (token != Token.TEXT_STRING) {
            throw badType("string", token);
        }
        return CborReadUtil.readTextString(payload, parser.getPosition(), parser.getItemLength());
    }

    @Override
    public Document readDocument() {
        var token = parser.currentToken();
        if (token == Token.FINISHED) {
            throw new SerializationException("No CBOR value to read");
        }
        return switch (token) {
            case Token.POS_INT, Token.NEG_INT -> Document.of(readLong(null));
            case Token.NULL -> null;
            case Token.TEXT_STRING -> Document.of(readString(null));
            case Token.BYTE_STRING -> Document.of(readBlob(null));
            case Token.TRUE -> Document.of(true);
            case Token.FALSE -> Document.of(false);
            case Token.EPOCH_INEG, Token.EPOCH_IPOS, Token.EPOCH_F -> Document.of(readTimestamp(null));
            case Token.FLOAT -> {
                int pos = parser.getPosition();
                int len = parser.getItemLength();
                long fp = CborReadUtil.readLong(payload, token, pos, len);
                // ordered by how likely it is we'll encounter each case
                if (len == 8) { // double
                    yield Document.of(Double.longBitsToDouble(fp));
                } else if (len == 4) { // float
                    yield Document.of(Float.intBitsToFloat((int) fp));
                } else { // b == 2  - half-precision float
                    yield Document.of(float16((int) fp));
                }
            }
            case Token.POS_BIGINT, Token.NEG_BIGINT -> Document.of(readBigInteger(null));
            case Token.BIG_DECIMAL -> Document.of(readBigDecimal(null));
            case Token.START_ARRAY -> {
                List<Document> values = new ArrayList<>();
                for (token = parser.advance(); token != Token.END_ARRAY; token = parser.advance()) {
                    values.add(readDocument());
                }
                yield Document.of(values);
            }
            case Token.START_OBJECT -> {
                Map<String, Document> values = new LinkedHashMap<>();
                for (token = parser.advance(); token != Token.END_OBJECT; token = parser.advance()) {
                    if (token != Token.KEY) {
                        throw badType("struct member", token);
                    }

                    var key = CborReadUtil.readTextString(payload, parser.getPosition(), parser.getItemLength());
                    parser.advance();
                    values.put(key, readDocument());
                }
                yield CborDocuments.of(values, settings);
            }
            default -> throw new SerializationException("Unexpected token: " + Token.name(token));
        };
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        byte token = parser.currentToken();
        byte actual = (byte) (token ^ Token.TAG_FLAG);
        if (actual <= Token.NEG_INT) {
            // Integer epoch-seconds timestamp
            return Instant.ofEpochSecond(readLong("timestamp", actual));
        } else if (actual == Token.FLOAT) {
            // Floating-point epoch-seconds with millisecond precision per RPCv2 spec
            double d = readDouble("timestamp", actual);
            return Instant.ofEpochMilli(Math.round(d * 1000d));
        }
        throw badType("timestamp", token);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        byte token = parser.currentToken();
        if (token == Token.FINISHED && schema.hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            // Empty input — treat as empty struct with no members.
            return;
        }
        if (token != Token.START_OBJECT) {
            throw badType("struct", token);
        }

        // Use Schema extension for O(1) lookup instead of ConcurrentHashMap
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(CborSchemaExtensions.KEY);
        CborMemberLookup lookup = ext != null ? ext.memberLookup() : null;
        int expectedNext = 0;

        for (token = parser.advance(); token != Token.END_OBJECT; token = parser.advance()) {
            if (token != Token.KEY) {
                throw badType("struct member", token);
            }

            int memberPos = parser.getPosition();
            int memberLen = parser.getItemLength();
            // don't dispatch any events for explicit nulls
            if (parser.advance() == Token.NULL) {
                continue;
            }

            Schema member = null;

            // Fast path: only for definite-length member names (the common case)
            if (!CborParser.isIndefinite(memberLen) && lookup != null) {
                // Speculative fast path: check expected next member via Arrays.equals
                if (expectedNext >= 0 && expectedNext < lookup.orderedNameBytes.length) {
                    byte[] expected = lookup.orderedNameBytes[expectedNext];
                    if (expected.length == memberLen
                            && Arrays.equals(
                                    payload,
                                    memberPos,
                                    memberPos + memberLen,
                                    expected,
                                    0,
                                    memberLen)) {
                        member = lookup.orderedSchemas[expectedNext];
                        expectedNext = member.memberIndex() + 1;
                    }
                }
                // Slow path: hash-based lookup
                if (member == null) {
                    member = lookup.lookup(payload, memberPos, memberPos + memberLen, -1);
                    if (member != null) {
                        expectedNext = member.memberIndex() + 1;
                    }
                }
            }

            // Fallback: string decode for indefinite-length or unknown members
            if (member == null) {
                String name = CborReadUtil.readTextString(payload, memberPos, memberLen);
                member = structSchema.member(name);
                if (member != null) {
                    expectedNext = member.memberIndex() + 1;
                } else {
                    consumer.unknownMember(state, name);
                    skipUnknownMember();
                    continue;
                }
            }

            consumer.accept(state, member, this);
        }
    }

    private void skipUnknownMember() {
        byte current = parser.currentToken();
        if (current != Token.START_OBJECT && current != Token.START_ARRAY) {
            return;
        }

        int depth = 0;
        while (true) {
            if (current == Token.START_OBJECT || current == Token.START_ARRAY) {
                depth++;
            } else if ((current == Token.END_OBJECT || current == Token.END_ARRAY) && --depth == 0) {
                return;
            }
            current = parser.advance();
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        byte token = parser.currentToken();
        if (token != Token.START_ARRAY) {
            throw badType("list", token);
        }

        for (token = parser.advance(); token != Token.END_ARRAY; token = parser.advance()) {
            consumer.accept(state, this);
        }
    }

    @Override
    public int containerSize() {
        return parser.collectionSize();
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        byte token = parser.currentToken();
        if (token != Token.START_OBJECT) {
            throw badType("struct", token);
        }

        for (token = parser.advance(); token != Token.END_OBJECT; token = parser.advance()) {
            if (token != Token.KEY) {
                throw badType("key", token);
            }
            var key = CborReadUtil.readTextString(payload, parser.getPosition(), parser.getItemLength());
            parser.advance();
            consumer.accept(state, key, this);
        }
    }

    @Override
    public boolean isNull() {
        return parser.currentToken() == Token.NULL;
    }

    @Override
    public <T> T readNull() {
        return null;
    }
}
