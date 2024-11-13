/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import static software.amazon.smithy.java.runtime.cbor.CborReadUtil.readByteString;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.cbor.CborParser.Token;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

final class CborDeserializer implements ShapeDeserializer {
    private static final class Canonicalizer {
        private record Canonical(Schema member, byte[] utf8) implements Comparable<Canonical> {
            @Override
            public int compareTo(Canonical o) {
                return Arrays.compare(utf8, o.utf8);
            }

            private Schema isSame(byte[] bytes, int off, int len) {
                if (Arrays.compare(utf8, 0, utf8.length, bytes, off, off + len) == 0) {
                    return member;
                }
                return null;
            }
        }

        private final Object[][] canonicals;

        Canonicalizer(Schema schema) {
            int biggest = 0;
            Map<Integer, List<Canonical>> bySize = new HashMap<>();
            for (var member : schema.members()) {
                byte[] utf8 = member.memberName().getBytes(StandardCharsets.UTF_8);
                biggest = Math.max(biggest, utf8.length);
                bySize.computeIfAbsent(utf8.length, $ -> new ArrayList<>())
                    .add(new Canonical(member, utf8));
            }

            canonicals = new Object[biggest + 1][];
            for (var entry : bySize.entrySet()) {
                int len = entry.getKey();
                var canonsForLen = entry.getValue().toArray(new Canonical[0]);
                Arrays.sort(canonsForLen);
                canonicals[len] = canonsForLen;
            }
        }

        Schema resolve(byte[] payload, int off, int len) {
            if (len >= canonicals.length) {
                return null;
            }

            Object[] canonicals = this.canonicals[len];
            if (canonicals == null) {
                return null;
            }

            if (canonicals.length == 1) {
                return getMemberIfSame(canonicals[0], payload, off, len);
            } else {
                for (int i = 0; i < canonicals.length; i++) {
                    var member = getMemberIfSame(canonicals[i], payload, off, len);
                    if (member != null) {
                        return member;
                    }
                }
                return null;
            }
        }

        private Schema getMemberIfSame(Object o, byte[] bytes, int off, int len) {
            return ((Canonical) o).isSame(bytes, off, len);
        }
    }

    private static final Map<Schema, Canonicalizer> CANONICALIZERS = new ConcurrentHashMap<>();

    private final CborParser parser;
    private final byte[] payload;

    CborDeserializer(byte[] payload) {
        this.parser = new CborParser(payload);
        this.payload = payload;
        parser.advance();
    }

    CborDeserializer(ByteBuffer byteBuffer) {
        if (byteBuffer.hasArray()) {
            byte[] payload = byteBuffer.array();
            this.payload = payload;
            this.parser = new CborParser(
                payload,
                byteBuffer.arrayOffset() + byteBuffer.position(),
                byteBuffer.remaining()
            );
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
        if (token > Token.NEG_INT) throw badType(type, token);
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
        int mant = hbits & 0x03ff;          // 10 bits mantissa
        int exp = hbits & 0x7c00;          // 5 bits exponent
        if (exp == 0x7c00) {                // NaN/Inf
            exp = 0x3fc00;                  // -> NaN/Inf
        } else if (exp != 0) {              // normalized value
            exp += 0x1c000;                 // exp - 15 + 127
            if (mant == 0 && exp > 0x1c400) // smooth transition
                return Float.intBitsToFloat((hbits & 0x8000) << 16 | exp << 13);
        } else if (mant != 0) {             // && exp==0 -> subnormal
            exp = 0x1c400;                  // make it normal
            do {
                mant <<= 1;                 // mantissa * 2
                exp -= 0x400;               // decrease exp by 1
            } while ((mant & 0x400) == 0);  // while not normal
            mant &= 0x3ff;                  // discard subnormal bit
        }                                   // else +/-0 -> +/-0
        return Float.intBitsToFloat(
            // combine all parts
            (hbits & 0x8000) << 16          // sign  << ( 31 - 15 )
                | (exp | mant) << 13        // value << ( 23 - 10 )
        );
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        return null;
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        return null;
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
        throw new UnsupportedOperationException("RPCv2 does not support Documents");
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        byte token = parser.currentToken();
        byte actual = (byte) (token ^ Token.TAG_FLAG);
        if (actual <= Token.NEG_INT) {
            return Instant.ofEpochMilli(readLong("timestamp", token) * 1000);
        } else if (actual == Token.FLOAT) {
            double d = readDouble("timestamp", actual);
            return Instant.ofEpochMilli(Math.round(d * 1000d));
        }
        throw badType("timestamp", token);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        byte token = parser.currentToken();
        if (token != Token.START_OBJECT) {
            throw badType("struct", token);
        }

        var canonicalizer = getCanonicalizer(schema);
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

            // wait to resolve the member until we know an event will be dispatched
            Object member = resolveMember(schema, canonicalizer, payload, memberPos, memberLen);
            if (member.getClass() == String.class) {
                consumer.unknownMember(state, (String) member);
                skipUnknownMember();
            } else {
                consumer.accept(state, (Schema) member, this);
            }
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

    private static Object resolveMember(Schema host, Canonicalizer canonicalizer, byte[] payload, int pos, int len) {
        // this method is static for safety. the parser has already advanced past the member field by the time
        // resolveMember is called, so don't touch the parser or any other instance state.
        if (CborParser.isIndefinite(len)) {
            return resolveSlow(host, payload, pos, len);
        }

        var schema = canonicalizer.resolve(payload, pos, len);
        if (schema != null) {
            return schema;
        }
        return CborReadUtil.readTextString(payload, pos, len);
    }

    private static Object resolveSlow(Schema host, byte[] payload, int pos, int len) {
        var name = CborReadUtil.readTextString(payload, pos, len);
        var schema = host.member(name);
        return schema != null ? schema : host;
    }

    private Canonicalizer getCanonicalizer(Schema schema) {
        var canonicalizer = CANONICALIZERS.get(schema);
        if (canonicalizer == null) {
            canonicalizer = new Canonicalizer(schema);
            CANONICALIZERS.put(schema, canonicalizer);
        }

        return canonicalizer;
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
