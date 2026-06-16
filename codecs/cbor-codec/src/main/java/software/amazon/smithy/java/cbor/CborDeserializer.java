/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborReadUtil.argLength;
import static software.amazon.smithy.java.cbor.CborReadUtil.readByteString;
import static software.amazon.smithy.java.cbor.CborReadUtil.readPosInt;
import static software.amazon.smithy.java.cbor.CborReadUtil.readStrLen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;

final class CborDeserializer implements ShapeDeserializer {
    private static final int MAX_DEPTH = 1000;

    static final class Token {
        public static final byte TAG_FLAG = 1 << 4;

        public static final byte POS_INT = TYPE_POSINT;
        public static final byte NEG_INT = TYPE_NEGINT;
        public static final byte BYTE_STRING = TYPE_BYTESTRING;
        public static final byte TEXT_STRING = TYPE_TEXTSTRING;

        public static final byte NULL = 4;
        public static final byte KEY = 5;
        public static final byte START_OBJECT = 6;
        public static final byte START_ARRAY = 7;
        public static final byte END_OBJECT = 8;
        public static final byte END_ARRAY = 9;

        public static final byte POS_BIGINT = 10;
        public static final byte NEG_BIGINT = 11;
        public static final byte FLOAT = 12;
        public static final byte BIG_DECIMAL = 14;
        public static final byte TRUE = 15;
        public static final byte FALSE = TRUE | TAG_FLAG;

        public static final byte EPOCH_IPOS = POS_INT | TAG_FLAG;
        public static final byte EPOCH_INEG = NEG_INT | TAG_FLAG;
        public static final byte EPOCH_F = FLOAT | TAG_FLAG;

        public static final byte FINISHED = -1;

        static String name(byte token) {
            return switch (token) {
                case POS_INT -> "POS_INT";
                case NEG_INT -> "NEG_INT";
                case BYTE_STRING -> "BYTE_STRING";
                case TEXT_STRING -> "TEXT_STRING";
                case POS_BIGINT -> "POS_BIGINT";
                case NEG_BIGINT -> "NEG_BIGINT";
                case FLOAT -> "FLOAT";
                case BIG_DECIMAL -> "BIG_DECIMAL";
                case TRUE -> "TRUE";
                case FALSE -> "FALSE";
                case EPOCH_IPOS -> "EPOCH_IPOS";
                case EPOCH_INEG -> "EPOCH_INEG";
                case EPOCH_F -> "EPOCH_F";
                case NULL -> "NULL";
                case KEY -> "KEY";
                case START_OBJECT -> "START_OBJECT";
                case START_ARRAY -> "START_ARRAY";
                case END_ARRAY -> "END_ARRAY";
                case END_OBJECT -> "END_OBJECT";
                case FINISHED -> "FINISHED";
                default -> throw new BadCborException("unknown token " + token);
            };
        }
    }

    static final int MAJOR_TYPE_SHIFT = 5,
            MAJOR_TYPE_MASK = 0b111_00000,
            MINOR_TYPE_MASK = 0b0001_1111;

    static final byte TYPE_POSINT = 0,
            TYPE_NEGINT = 1,
            TYPE_BYTESTRING = 2,
            TYPE_TEXTSTRING = 3,
            TYPE_ARRAY = 4,
            TYPE_MAP = 5,
            TYPE_TAG = 6,
            TYPE_SIMPLE = 7;

    static final int ZERO_BYTES = 23,
            ONE_BYTE = 24,
            EIGHT_BYTES = 27,
            INDEFINITE = 31;

    static final int SIMPLE_FALSE = 20,
            SIMPLE_TRUE = 21,
            SIMPLE_NULL = 22,
            SIMPLE_UNDEFINED = 23,
            SIMPLE_VALUE_1 = 24,
            SIMPLE_HALF_FLOAT = 25,
            SIMPLE_FLOAT = 26,
            SIMPLE_DOUBLE = 27;

    static final byte SIMPLE_STREAM_BREAK = (byte) ((TYPE_SIMPLE << MAJOR_TYPE_SHIFT) | INDEFINITE);

    static final byte TAG_TIME_RFC3339 = 0,
            TAG_TIME_EPOCH = 1,
            TAG_POS_BIGNUM = 2,
            TAG_NEG_BIGNUM = 3,
            TAG_DECIMAL = 4;

    private static final int FLAG_INDEFINITE_LEN = 1 << 31;
    private static final int MASK_LEN = ~FLAG_INDEFINITE_LEN;

    static boolean isIndefinite(int itemLength) {
        return itemLength < 0;
    }

    static int itemLength(int itemLength) {
        return itemLength & MASK_LEN;
    }

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
    private int depth;

    CborDeserializer(byte[] payload, CborSettings settings) {
        this.payload = payload;
        this.settings = settings;
        this.end = payload.length;
        this.idx = 0;
        this.depth = 0;
        advance();
    }

    CborDeserializer(ByteBuffer byteBuffer, CborSettings settings) {
        this.settings = settings;
        if (byteBuffer.hasArray()) {
            this.payload = byteBuffer.array();
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

    private byte advance() {
        return (token = nextToken0());
    }

    private byte nextToken0() {
        if (inCollection) {
            long state = currentState;
            if (state >> 1 == 0) {
                return getEndToken(state);
            } else if ((state & 3) == 0) {
                int i = (idx += itemLength(itemLength) + overhead);
                if (i >= end) {
                    throwIncompleteCollectionException();
                }
                return dispatchKey(payload[i]);
            }
        }

        int i = (idx += itemLength(itemLength) + overhead);
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
        overhead = 1;
        itemLength = 0;
        readingTag = true;
        byte next = advance();
        readingTag = false;
        switch (minor) {
            case TAG_TIME_EPOCH:
                if (next != Token.FLOAT && next > Token.NEG_INT)
                    throw new BadCborException("malformed instant: got " + Token.name(next));
                return (byte) (next | Token.TAG_FLAG);
            case TAG_POS_BIGNUM:
                if (next != Token.BYTE_STRING)
                    throw new BadCborException("malformed +bignum: got " + Token.name(next));
                return Token.POS_BIGINT;
            case TAG_NEG_BIGNUM:
                if (next != Token.BYTE_STRING)
                    throw new BadCborException("malformed -bignum: got " + Token.name(next));
                return Token.NEG_BIGINT;
            case TAG_DECIMAL:
                tagDecimalFp(next);
                return Token.BIG_DECIMAL;
            default:
                throw new BadCborException("unsupported tag minor " + minor);
        }
    }

    private void tagDecimalFp(byte next) {
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
        itemLength = idx - start + itemLength(itemLength) + overhead - 1;
        overhead = 1;
        idx = start;
    }

    private static void badDecimalInitialType(byte next) {
        throw new BadCborException("malformed BIG_DECIMAL: got " + Token.name(next));
    }

    private static void badDecimalArgument1(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected int 1, got " + Token.name(token));
    }

    private static void badDecimalArgument2(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected int 2, got " + Token.name(token));
    }

    private static void badDecimalFinalToken(byte token) {
        throw new BadCborException("malformed BIG_DECIMAL: expected END_ARRAY, got " + Token.name(token));
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
            integer(major, minor);
            return Token.FLOAT;
        } else if (minor == INDEFINITE) {
            return endStreamCollection();
        }
        throw new BadCborException("illegal simple minor type " + minor);
    }

    private byte endStreamCollection() {
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
            idx++;
            return minor;
        } else {
            int ret = readPosInt(payload, ++idx, argLen);
            idx += argLen;
            return ret;
        }
    }

    private byte collection(byte major, byte minor) {
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
        } else {
            currentState = size << 2;
            return Token.START_OBJECT;
        }
    }

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
        itemLength |= FLAG_INDEFINITE_LEN;
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
            if (isIndefinite(len)) {
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
        if (token == Token.FLOAT) {
            return readDouble(token);
        } else if (token == Token.POS_INT) {
            // Tolerate an integer-encoded value for a floating-point member: a whole-valued double (e.g. 1.0)
            // is commonly written as a CBOR integer by other encoders, so coerce instead of rejecting.
            long val = CborReadUtil.readLong(payload, token, idx, itemLength);
            // Lengths below 8 are zero-extended, so only a value above Long.MAX_VALUE can wrap to negative.
            if (val < 0) {
                throw new SerializationException("Cannot read a bignum-sized integer as a " + type);
            }
            return val;
        } else if (token == Token.NEG_INT) {
            long val = CborReadUtil.readLong(payload, token, idx, itemLength);
            // Likewise, only a value below Long.MIN_VALUE can wrap to >= 0.
            if (val >= 0) {
                throw new SerializationException("Cannot read a bignum-sized integer as a " + type);
            }
            return val;
        } else {
            throw badType(type, token);
        }
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
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
                }
                List<Document> values = new ArrayList<>();
                for (token = advance(); token != Token.END_ARRAY; token = advance()) {
                    values.add(readDocument());
                }
                depth--;
                yield Document.of(values);
            }
            case Token.START_OBJECT -> {
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
                }
                Map<String, Document> values = new LinkedHashMap<>();
                for (token = advance(); token != Token.END_OBJECT; token = advance()) {
                    if (token != Token.KEY) {
                        throw badType("struct member", token);
                    }

                    var key = CborReadUtil.readTextString(payload, idx, itemLength);
                    advance();
                    values.put(key, readDocument());
                }
                depth--;
                yield CborDocuments.of(values, settings);
            }
            default -> throw new SerializationException("Unexpected token: " + Token.name(token));
        };
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        byte token = this.token;
        byte actual = (byte) (token ^ Token.TAG_FLAG);
        try {
            if (actual <= Token.NEG_INT) {
                return Instant.ofEpochSecond(readLong("timestamp", actual));
            } else if (actual == Token.FLOAT) {
                double d = readDouble("timestamp", actual);
                return Instant.ofEpochMilli(Math.round(d * 1000d));
            }
        } catch (DateTimeException e) {
            throw new SerializationException("timestamp out of range", e);
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
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
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

            if (!isIndefinite(memberLen) && lookup != null) {
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
                if (member == null) {
                    member = lookup.lookup(payload, memberPos, memberPos + memberLen, -1);
                    if (member != null) {
                        expectedNext = member.memberIndex() + 1;
                    }
                }
            }

            if (member == null) {
                expectedNext = resolveMemberFallback(
                        structSchema,
                        memberPos,
                        memberLen,
                        expectedNext,
                        state,
                        consumer);
                continue;
            }

            consumer.accept(state, member, this);
        }
        depth--;
    }

    private void readStructEmpty(Schema schema, byte token) {
        if (token == Token.FINISHED && schema.hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            return;
        }
        throw badType("struct", token);
    }

    private <T> int resolveMemberFallback(
            Schema structSchema,
            int memberPos,
            int memberLen,
            int expectedNext,
            T state,
            StructMemberConsumer<T> consumer
    ) {
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
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        byte token = this.token;
        if (token != Token.START_ARRAY) {
            throw badType("list", token);
        }

        for (token = advance(); token != Token.END_ARRAY; token = advance()) {
            consumer.accept(state, this);
        }
        depth--;
    }

    @Override
    public int containerSize() {
        return collectionSize();
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        depth++;
        if (depth > MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
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
        depth--;
    }

    @Override
    public boolean isNull() {
        return token == Token.NULL;
    }

}
