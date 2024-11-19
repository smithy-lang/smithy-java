/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborParser.Token.name;
import static software.amazon.smithy.java.cbor.CborReadUtil.argLength;
import static software.amazon.smithy.java.cbor.CborReadUtil.readPosInt;
import static software.amazon.smithy.java.cbor.CborReadUtil.readStrLen;

import java.util.Arrays;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CborParser {
    public static final class Token {
        public static int version() { return 1; }

        // high bit toggle to indicate a tagged item
        public static final byte TAG_FLAG = 1 << 4;

        // the first group of simple types directly map to their cbor major types for simpler reading routines
        public static final byte POS_INT = TYPE_POSINT;            // 0b00000
        public static final byte NEG_INT = TYPE_NEGINT;            // 0b00001
        public static final byte BYTE_STRING = TYPE_BYTESTRING;        // 0b00010
        public static final byte TEXT_STRING = TYPE_TEXTSTRING;        // 0b00011

        // types that require explicit method invocations on ReadTranslator
        // these values must be contiguous for efficient lookup table generation
        public static final byte NULL = 4;                      // 0b00100
        public static final byte KEY = 5;                      // 0b00101
        public static final byte START_OBJECT = 6;                      // 0b00110
        public static final byte START_ARRAY = 7;                      // 0b00111
        public static final byte END_OBJECT = 8;                      // 0b01000
        public static final byte END_ARRAY = 9;                      // 0b01001

        // the second group of simple types have arbitrary values and can pretty much be anything
        public static final byte POS_BIGINT = 10;                     // 0b01010
        public static final byte NEG_BIGINT = 11;                     // 0b01011
        public static final byte FLOAT = 12;                     // 0b01100
        // gap: we used to have a bigfloat token, but we never supported that type
        public static final byte BIG_DECIMAL = 14;                     // 0b01110
        public static final byte TRUE = 15;                     // 0b01111
        public static final byte FALSE = TRUE | TAG_FLAG; // 0b11111 (31)

        // tag types are the type of the tagged data with the high bit set
        // these do not need to be contiguous
        public static final byte EPOCH_IPOS = POS_INT | TAG_FLAG; // 0b10000 (16)
        public static final byte EPOCH_INEG = NEG_INT | TAG_FLAG; // 0b10001 (17)
        public static final byte EPOCH_F = FLOAT | TAG_FLAG; // 0b11100 (28)

        public static final byte FINISHED = -1;

        public static String name(byte token) {
            switch (token) {
                case POS_INT:
                    return "POS_INT";
                case NEG_INT:
                    return "NEG_INT";
                case BYTE_STRING:
                    return "BYTE_STRING";
                case TEXT_STRING:
                    return "TEXT_STRING";
                case POS_BIGINT:
                    return "POS_BIGINT";
                case NEG_BIGINT:
                    return "NEG_BIGINT";
                case FLOAT:
                    return "FLOAT";
                case BIG_DECIMAL:
                    return "BIG_DECIMAL";
                case TRUE:
                    return "TRUE";
                case FALSE:
                    return "FALSE";
                case EPOCH_IPOS:
                    return "EPOCH_IPOS";
                case EPOCH_INEG:
                    return "EPOCH_INEG";
                case EPOCH_F:
                    return "EPOCH_F";
                case NULL:
                    return "NULL";
                case KEY:
                    return "KEY";
                case START_OBJECT:
                    return "START_OBJECT";
                case START_ARRAY:
                    return "START_ARRAY";
                case END_ARRAY:
                    return "END_ARRAY";
                case END_OBJECT:
                    return "END_OBJECT";
            }
            if (token == FINISHED) return "FINISHED";
            throw new BadCborException("unknown token " + token);
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
        SIMPLE_VALUE_1 = 24, // value follows in next 1 byte, currently reserved and unused
        SIMPLE_HALF_FLOAT = 25,
        SIMPLE_FLOAT = 26,
        SIMPLE_DOUBLE = 27;

    static final byte SIMPLE_STREAM_BREAK = (byte) ((TYPE_SIMPLE << MAJOR_TYPE_SHIFT) | INDEFINITE);

    static final byte TAG_TIME_RFC3339 = 0, // expect text string
        TAG_TIME_EPOCH = 1, // expect integer or float
        TAG_POS_BIGNUM = 2, // expect byte string
        TAG_NEG_BIGNUM = 3, // expect byte string
        TAG_DECIMAL = 4; // expect two-element integer array

    private static final int FLAG_INDEFINITE_LEN = 1 << 31;
    private static final int MASK_LEN = ~FLAG_INDEFINITE_LEN;

    public static boolean isIndefinite(int itemLength) {
        return itemLength < 0;
    }

    public static int itemLength(int itemLength) {
        return itemLength & MASK_LEN;
    }

    private final byte[] buffer;
    private final int len;
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

    public CborParser(byte[] buffer) {
        this(buffer, 0, buffer.length);
    }

    public CborParser(byte[] buffer, int off, int len) {
        this.buffer = buffer;
        this.idx = off;
        this.len = len;
    }

    /**
     * @return the starting position of the current data item
     */
    public int getPosition() {
        return idx;
    }

    /**
     * If the last token returned by {@link #advance()} is a single data item (e.g. a numeric type),
     * this indicates the number of bytes from {@link #getPosition()} to read to retrieve it.
     *
     * <p>This method does not return a defined result for collection types like strings, arrays, or maps.
     *
     * @return number of bytes encoding the current single-element data item, or undefined
     */
    public int getItemLength() {
        return itemLength;
    }

    public int collectionSize() {
        long s = currentState >> 2;
        return s >= 0 ? (int) s : -1;
    }

    public byte currentToken() {
        return token;
    }

    /**
     * Gets the next {@link Token} in the payload. The data for this token begins at {@link #getPosition()}. The data's
     * length is determined by {@link #getItemLength()} if the token is <b>not</b> one of these types:
     *
     * <ul>
     *     <li>{@link Token#NULL}</li>
     *     <li>{@link Token#START_ARRAY}</li>
     *     <li>{@link Token#END_ARRAY}</li>
     *     <li>{@link Token#START_OBJECT}</li>
     *     <li>{@link Token#END_OBJECT}</li>
     * </ul>
     *
     * @return the next {@link Token} in the payload
     */
    public byte advance() {
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
                int i = (idx += itemLength(itemLength) + overhead);
                if (i >= len) {
                    throwIncompleteCollectionException();
                }
                return dispatchKey(buffer[i]);
            }
        }

        int i = (idx += itemLength(itemLength) + overhead);
        if (i >= len) {
            return endOfBuffer(i);
        }

        return dispatch(buffer[i]);
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
        if (i > len) {
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
        if (readingTag) throw new BadCborException("nested tags not permitted");
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
        itemLength = idx - start + itemLength(itemLength) + overhead - 1;
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
        if (minor == INDEFINITE) throw new BadCborException("numeric type has indefinite length");
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

    /**
     * Reads a {@linkplain #TYPE_TEXTSTRING text string} or {@linkplain #TYPE_BYTESTRING bytestring}
     * from the buffer.
     *
     * <p>Definite length strings have no overhead. {@code idx} will point to the start of the string
     * data and {@code itemLength} will be the number of bytes in the string. The next data item begins
     * at {@code idx + itemLength}.
     *
     * <p>Indefinite length strings are a bit trickier. {@code idx} will point to the start of the first
     * string in the sequence and {@code itemLength} will be the number of bytes in the final assembled
     * string. However, this is <b>not</b> the number of bytes that this string spans in the CBOR payload.
     * We use a separate count, {@linkplain #overhead}, to factor in the additional overhead of encoding
     * non-data tags. We add all opening tag bytes, length operands, and the final closing {@link #INDEFINITE}
     * byte to this value.
     */
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
            int ret = readPosInt(buffer, ++idx, argLen);
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
            if (scan >= len) throw new BadCborException("non-terminating string");
            byte b = buffer[scan];
            if (b == SIMPLE_STREAM_BREAK) {
                overhead++;
                break;
            }
            int major = (b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT;
            int minor = b & MINOR_TYPE_MASK;
            if (major != type) {
                throw new BadCborException("major type misalign: " + type + " " + major);
            }
            if (minor == INDEFINITE) throw new BadCborException("expected finite length");
            int argLen = argLength(minor);
            int strLen = readStrLen(buffer, scan, minor, argLen);
            int totalOverhead = argLen + 1;
            overhead += totalOverhead;
            itemLength += strLen;
            scan += totalOverhead + strLen;
        }
        itemLength |= FLAG_INDEFINITE_LEN;
    }

    private void throwIncompleteCollectionException() {
        String type = (currentState & 1L) == 0 ? "map" : "array";
        String msg = currentState < 0 ? "stream break" : ((currentState >> 1) + " more elements");
        throw new BadCborException("incomplete " + type + ": expecting " + msg);
    }
}
