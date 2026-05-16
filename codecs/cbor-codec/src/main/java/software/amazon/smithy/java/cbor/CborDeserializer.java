/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborParser.INDEFINITE;
import static software.amazon.smithy.java.cbor.CborParser.MAJOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborParser.MAJOR_TYPE_SHIFT;
import static software.amazon.smithy.java.cbor.CborParser.MINOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_DOUBLE;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_FALSE;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_NULL;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_STREAM_BREAK;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_TRUE;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_UNDEFINED;
import static software.amazon.smithy.java.cbor.CborParser.SIMPLE_VALUE_1;
import static software.amazon.smithy.java.cbor.CborParser.TAG_DECIMAL;
import static software.amazon.smithy.java.cbor.CborParser.TAG_NEG_BIGNUM;
import static software.amazon.smithy.java.cbor.CborParser.TAG_POS_BIGNUM;
import static software.amazon.smithy.java.cbor.CborParser.TAG_TIME_EPOCH;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_ARRAY;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_BYTESTRING;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_MAP;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_NEGINT;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_POSINT;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_SIMPLE;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_TAG;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_TEXTSTRING;
import static software.amazon.smithy.java.cbor.CborParser.Token.name;
import static software.amazon.smithy.java.cbor.CborReadUtil.argLength;
import static software.amazon.smithy.java.cbor.CborReadUtil.readByteString;
import static software.amazon.smithy.java.cbor.CborReadUtil.readPosInt;
import static software.amazon.smithy.java.cbor.CborReadUtil.readStrLen;

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

    private final CborSettings settings;
    private final byte[] payload;
    private final int end;
    private int idx;
    private byte token;

    // Definite sizes shrink to zero, indefinite sizes start at -1 and decrement meaninglessly towards Long.MIN_VALUE.
    // Must be long because we need to store 2 * size for maps, and a map can have up to Integer.MAX_VALUE elements.
    // Count is left shifted one. Low bit is collection type: 0 == map, 1 == array.
    private long currentState = 0;
    private long[] previousStates = new long[4];
    private boolean inCollection = false;
    private int historyDepth = 0;
    private int itemLength = 0;
    private int overhead = 0; // overhead is [0,8]
    private boolean readingTag = false;

    CborDeserializer(byte[] payload, CborSettings settings) {
        this.payload = payload;
        this.settings = settings;
        this.end = payload.length;
        this.idx = 0;
        advance();
    }

    CborDeserializer(ByteBuffer byteBuffer, CborSettings settings) {
        this.settings = settings;
        if (byteBuffer.hasArray()) {
            byte[] payload = byteBuffer.array();
            this.payload = payload;
            int start = byteBuffer.arrayOffset() + byteBuffer.position();
            this.idx = start;
            this.end = start + byteBuffer.remaining();
        } else {
            int pos = byteBuffer.position();
            this.payload = new byte[byteBuffer.remaining()];
            byteBuffer.get(this.payload);
            this.idx = 0;
            this.end = this.payload.length;
            byteBuffer.position(pos);
        }
        advance();
    }

    // ---- Parser methods (moved from CborParser) ----

    private byte advance() {
        return (token = nextToken0());
    }

    private byte nextToken0() {
        if (inCollection) {
            long state = currentState;
            if (state >> 1 == 0) {
                // count is 0, so the only remaining value is the collection type in the low bit
                return getEndToken(state);
            } else if ((state & 3) == 0) {
                // mask is 0b11: low bit is collection type (map == 0), high bit is 0 if the count is even
                int i = (idx += CborParser.itemLength(itemLength) + overhead);
                if (i >= end) {
                    throwIncompleteCollectionException();
                }
                return dispatchKey(payload[i]);
            }
        }

        int i = (idx += CborParser.itemLength(itemLength) + overhead);
        if (i >= end) {
            return endOfBuffer(i);
        }

        return dispatch(payload[i]);
    }

    private byte dispatchKey(byte b) {
        byte major = (byte) ((b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT);
        if (major == TYPE_TEXTSTRING) {
            byte minor = (byte) (b & MINOR_TYPE_MASK);
            string(major, minor);
            return Token.KEY;
        } else if (b == SIMPLE_STREAM_BREAK) {
            return endStreamCollection();
        } else {
            throw new BadCborException("map keys must be strings");
        }
    }

    private byte endOfBuffer(int i) {
        itemLength = 0;
        overhead = 0;
        if (i > end) {
            throw new BadCborException("unexpected end of payload");
        }
        if (inCollection) {
            throwIncompleteCollectionException();
        }
        return Token.FINISHED;
    }

    private byte getEndToken(long state) {
        byte retVal = state == 0 ? Token.END_OBJECT : Token.END_ARRAY;
        if (historyDepth > 0) {
            currentState = previousStates[--historyDepth];
        } else {
            inCollection = false;
            currentState = 0;
        }
        return retVal;
    }

    private byte dispatch(byte b) {
        byte major = (byte) ((b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT);
        byte minor = (byte) (b & MINOR_TYPE_MASK);
        // major is guaranteed in range [0,7] by the mask-and-shift operation
        switch (major) {
            case TYPE_POSINT:
            case TYPE_NEGINT:
                return integer(major, minor);
            case TYPE_BYTESTRING:
            case TYPE_TEXTSTRING:
                return string(major, minor);
            case TYPE_ARRAY:
            case TYPE_MAP:
                return collection(major, minor);
            case TYPE_TAG:
                return tag(minor);
            case TYPE_SIMPLE:
                return simple(major, minor);
            default:
                throw new BadCborException("unknown major type: " + major);
        }
    }

    private byte tag(byte minor) {
        // RFC8949 3.4 permits nested tags, but I see no need to support anything beyond the simple
        // tags that are relevant to the Smithy object model.
        if (readingTag)
            throw new BadCborException("nested tags not permitted");
        // reset increments before calling nextToken. 1 overhead for this tag /immediate value
        overhead = 1;
        itemLength = 0;
        readingTag = true;
        byte next = advance();
        readingTag = false;
        switch (minor) {
            case TAG_TIME_EPOCH:
                if (next != Token.FLOAT && next > Token.NEG_INT)
                    throw new BadCborException("malformed instant: got " + name(next));
                return (byte) (next | Token.TAG_FLAG);
            case TAG_POS_BIGNUM:
                if (next != Token.BYTE_STRING)
                    throw new BadCborException("malformed +bignum: got " + name(next));
                return Token.POS_BIGINT;
            case TAG_NEG_BIGNUM:
                if (next != Token.BYTE_STRING)
                    throw new BadCborException("malformed -bignum: got " + name(next));
                return Token.NEG_BIGINT;
            case TAG_DECIMAL:
                tagDecimalFp(next);
                return Token.BIG_DECIMAL;
            default:
                throw new BadCborException("unsupported tag minor " + minor);
        }
    }

    private void tagDecimalFp(byte next) {
        // A decimal fraction or a bigfloat is represented as a tagged array that contains
        // exactly an integer and a bignum/integer
        if (next != Token.START_ARRAY)
            badDecimalInitialType(next);
        int start = idx;
        byte token;
        if ((token = advance()) > Token.NEG_INT)
            badDecimalArgument1(token);
        token = advance();
        int tmp = token & 0b11110;
        if (tmp != Token.POS_INT && tmp != Token.POS_BIGINT)
            badDecimalArgument2(token);
        if ((token = advance()) != Token.END_ARRAY)
            badDecimalFinalToken(token);
        itemLength = idx - start + CborParser.itemLength(itemLength) + overhead - 1;
        overhead = 1;
        idx = start;
    }

    private static void badDecimalInitialType(byte next) {
        throw new BadCborException("malformed BIG_DECIMAL: got " + name(next));
    }

    private static void badDecimalArgument1(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected int 1, got " + name(token));
    }

    private static void badDecimalArgument2(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected int 2, got " + name(token));
    }

    private static void badDecimalFinalToken(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected END_ARRAY, got " + name(token));
    }

    private byte integer(byte major, byte minor) {
        if (minor == INDEFINITE)
            throw new BadCborException("numeric type has indefinite length");
        int argLength = argLength(minor);
        if (argLength > 0) {
            overhead = 0;
            idx++;
        } else {
            overhead = 1;
        }
        itemLength = argLength;
        // 2 because the count is left-shifted one (2 == 1 << 1)
        currentState -= 2;
        return major;
    }

    private byte simple(byte major, byte minor) {
        if (minor <= SIMPLE_VALUE_1) {
            currentState -= 2;
            itemLength = 1;
            overhead = 0;
            switch (minor) {
                case SIMPLE_FALSE:
                    return Token.FALSE;
                case SIMPLE_TRUE:
                    return Token.TRUE;
                case SIMPLE_NULL:
                case SIMPLE_UNDEFINED:
                    return Token.NULL;
                default:
                    throw new BadCborException("bad simple minor type " + minor);
            }
        } else if (minor <= SIMPLE_DOUBLE) {
            // collectionSize is decremented in integer if necessary
            integer(major, minor);
            return Token.FLOAT;
        } else if (minor == INDEFINITE) {
            return endStreamCollection();
        }
        throw new BadCborException("illegal simple minor type " + minor);
    }

    private byte endStreamCollection() {
        // no need to decrement collectionSize in this branch since we're in an indefinite collection
        itemLength = 0;
        overhead = 1;
        // note that we can leave the collection type in the low bit. all that matters is that
        // the number is negative, and the low bit will only make a positive number more positive
        // and a negative number more negative.
        if (!inCollection || currentState >= 0)
            throw new BadCborException("unexpected indefinite terminator");
        long state = currentState;
        if (historyDepth > 0) {
            currentState = previousStates[--historyDepth];
        } else {
            inCollection = false;
            currentState = 0;
        }
        return (state & 1) == 0 ? Token.END_OBJECT : Token.END_ARRAY;
    }

    private byte string(byte major, byte minor) {
        overhead = 0;
        if (minor == INDEFINITE) {
            readIndefiniteLength(major);
        } else {
            int argLen = argLength(minor);
            itemLength = readImm(minor, argLen);
        }
        currentState -= 2;
        return major;
    }

    private int readImm(int minor, int argLen) {
        if (argLen == 0) {
            // minor is the collection/string length, data begins on next byte
            idx++;
            return minor;
        } else {
            // minor is the number of bytes following this one that encode the collection/string length
            int ret = readPosInt(payload, ++idx, argLen);
            idx += argLen;
            return ret;
        }
    }

    private byte collection(byte major, byte minor) {
        // collection length is tracked in collectionSizes
        itemLength = 0;
        long size;
        if (minor == INDEFINITE) {
            overhead = 1;
            size = -1;
        } else {
            int argLen = argLength(minor);
            overhead = 0;
            size = readImm(minor, argLen);
        }
        if (inCollection) {
            currentState -= 2;
            if (historyDepth == previousStates.length) {
                previousStates = Arrays.copyOf(previousStates, previousStates.length * 2);
            }
            previousStates[historyDepth++] = currentState;
        }
        inCollection = true;
        if (major == TYPE_ARRAY) {
            currentState = (size << 1) | 1;
            return Token.START_ARRAY;
        } else { //major == TYPE_MAP
            currentState = size << 2;
            return Token.START_OBJECT;
        }
    }

    // idx = start of string, itemLength = byte count, overhead = tag bytes
    private void readIndefiniteLength(byte type) {
        itemLength = 0;
        int scan = ++idx;
        while (true) {
            if (scan >= end)
                throw new BadCborException("non-terminating string");
            byte b = payload[scan];
            if (b == SIMPLE_STREAM_BREAK) {
                overhead++;
                break;
            }
            int major = (b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT;
            int minor = b & MINOR_TYPE_MASK;
            if (major != type) {
                throw new BadCborException("major type misalign: " + type + " " + major);
            }
            if (minor == INDEFINITE)
                throw new BadCborException("expected finite length");
            int argLen = argLength(minor);
            int strLen = readStrLen(payload, scan, minor, argLen);
            int totalOverhead = argLen + 1;
            overhead += totalOverhead;
            itemLength += strLen;
            scan += totalOverhead + strLen;
        }
        itemLength |= (1 << 31); // FLAG_INDEFINITE_LEN
    }

    private int collectionSize() {
        long s = currentState >> 2;
        return s >= 0 ? (int) s : -1;
    }

    private void throwIncompleteCollectionException() {
        String type = (currentState & 1L) == 0 ? "map" : "array";
        String msg = currentState < 0 ? "stream break" : ((currentState >> 1) + " more elements");
        throw new BadCborException("incomplete " + type + ": expecting " + msg);
    }

    // ---- Deserializer methods ----

    @Override
    public void close() {
        if (token != Token.FINISHED) {
            throw new SerializationException("Unexpected CBOR content at end of object");
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        byte token = this.token;
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
        byte token = this.token;
        if (token == Token.BYTE_STRING) {
            int pos = idx;
            int len = itemLength;
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
        return (byte) readLong("byte", this.token);
    }

    @Override
    public short readShort(Schema schema) {
        return (short) readLong("short", this.token);
    }

    @Override
    public int readInteger(Schema schema) {
        return (int) readLong("integer", this.token);
    }

    @Override
    public long readLong(Schema schema) {
        return readLong("long", this.token);
    }

    private long readLong(String type, byte token) {
        int off = idx;
        int len = itemLength;
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
        return (float) readDouble("float", this.token);
    }

    @Override
    public double readDouble(Schema schema) {
        return readDouble("double", this.token);
    }

    private double readDouble(String type, byte token) {
        if (token != Token.FLOAT) {
            throw badType(type, token);
        }
        return readDouble(token);
    }

    private double readDouble(byte token) {

        int pos = idx;
        int len = itemLength;
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
        byte token = this.token;
        int tmp = token & 0b11110;
        if (tmp != Token.POS_INT && tmp != Token.POS_BIGINT) {
            throw badType("biginteger", token);
        }
        return CborReadUtil.readBigInteger(payload, token, idx, itemLength);
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        byte token = this.token;
        if (token == Token.BIG_DECIMAL) {
            return CborReadUtil.readBigDecimal(payload, idx);
        } else if (token == Token.FLOAT) {
            return BigDecimal.valueOf(readDouble(token));
        } else if (token <= Token.NEG_INT) {
            return BigDecimal
                    .valueOf(CborReadUtil.readLong(payload, token, idx, itemLength));
        }
        throw badType("bigdecimal", token);
    }

    @Override
    public String readString(Schema schema) {
        byte token = this.token;
        if (token != Token.TEXT_STRING) {
            throw badType("string", token);
        }
        return CborReadUtil.readTextString(payload, idx, itemLength);
    }

    @Override
    public Document readDocument() {
        var token = this.token;
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
                int pos = idx;
                int len = itemLength;
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
                for (token = advance(); token != Token.END_ARRAY; token = advance()) {
                    values.add(readDocument());
                }
                yield Document.of(values);
            }
            case Token.START_OBJECT -> {
                Map<String, Document> values = new LinkedHashMap<>();
                for (token = advance(); token != Token.END_OBJECT; token = advance()) {
                    if (token != Token.KEY) {
                        throw badType("struct member", token);
                    }

                    var key = CborReadUtil.readTextString(payload, idx, itemLength);
                    advance();
                    values.put(key, readDocument());
                }
                yield CborDocuments.of(values, settings);
            }
            default -> throw new SerializationException("Unexpected token: " + Token.name(token));
        };
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        byte token = this.token;
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
        byte token = this.token;
        if (token != Token.START_OBJECT) {
            readStructEmpty(schema, token);
            return;
        }

        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(CborSchemaExtensions.KEY);
        CborMemberLookup lookup = ext != null ? ext.memberLookup() : null;
        int expectedNext = 0;

        for (token = advance(); token != Token.END_OBJECT; token = advance()) {
            if (token != Token.KEY) {
                throw badType("struct member", token);
            }

            int memberPos = idx;
            int memberLen = itemLength;
            if (advance() == Token.NULL) {
                continue;
            }

            Schema member = null;

            if (!CborParser.isIndefinite(memberLen) && lookup != null) {
                if (expectedNext >= 0 && expectedNext < lookup.orderedNameBytes.length) {
                    byte[] expected = lookup.orderedNameBytes[expectedNext];
                    if (expected.length == memberLen
                            && Arrays.equals(
                                    payload, memberPos, memberPos + memberLen,
                                    expected, 0, memberLen)) {
                        member = lookup.orderedSchemas[expectedNext];
                        expectedNext = member.memberIndex() + 1;
                    }
                }
                if (member == null) {
                    member = lookup.lookup(payload, memberPos, memberPos + memberLen, -1);
                    if (member != null) {
                        expectedNext = member.memberIndex() + 1;
                    }
                }
            }

            if (member == null) {
                expectedNext = resolveMemberFallback(
                        structSchema, memberPos, memberLen, expectedNext, state, consumer);
                continue;
            }

            consumer.accept(state, member, this);
        }
    }

    private void readStructEmpty(Schema schema, byte token) {
        if (token == Token.FINISHED && schema.hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            return;
        }
        throw badType("struct", token);
    }

    private <T> int resolveMemberFallback(
            Schema structSchema, int memberPos, int memberLen, int expectedNext,
            T state, StructMemberConsumer<T> consumer) {
        String name = CborReadUtil.readTextString(payload, memberPos, memberLen);
        Schema member = structSchema.member(name);
        if (member != null) {
            consumer.accept(state, member, this);
            return member.memberIndex() + 1;
        }
        consumer.unknownMember(state, name);
        skipUnknownMember();
        return expectedNext;
    }

    private void skipUnknownMember() {
        byte current = token;
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
            current = advance();
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        byte token = this.token;
        if (token != Token.START_ARRAY) {
            throw badType("list", token);
        }

        for (token = advance(); token != Token.END_ARRAY; token = advance()) {
            consumer.accept(state, this);
        }
    }

    @Override
    public int containerSize() {
        return collectionSize();
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        byte token = this.token;
        if (token != Token.START_OBJECT) {
            throw badType("struct", token);
        }

        for (token = advance(); token != Token.END_OBJECT; token = advance()) {
            if (token != Token.KEY) {
                throw badType("key", token);
            }
            var key = CborReadUtil.readTextString(payload, idx, itemLength);
            advance();
            consumer.accept(state, key, this);
        }
    }

    @Override
    public boolean isNull() {
        return token == Token.NULL;
    }

    @Override
    public <T> T readNull() {
        return null;
    }
}
