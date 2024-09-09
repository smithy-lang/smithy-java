/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import static software.amazon.smithy.java.runtime.json.jsoniter.internal.IterImpl.nextToken;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class JsonIterator implements Closeable {

    private static final int MAX_DEPTH = 128;

    final static ValueType[] valueTypes = new ValueType[256];
    byte[] buf;
    int head;
    int tail;

    int depth = 0;
    boolean closed;
    JsonException error;

    final Slice reusableSlice = new Slice(null, 0, 0);
    char[] reusableChars = new char[32];

    static {
        Arrays.fill(valueTypes, ValueType.INVALID);
        valueTypes['"'] = ValueType.STRING;
        valueTypes['-'] = ValueType.NUMBER;
        valueTypes['0'] = ValueType.NUMBER;
        valueTypes['1'] = ValueType.NUMBER;
        valueTypes['2'] = ValueType.NUMBER;
        valueTypes['3'] = ValueType.NUMBER;
        valueTypes['4'] = ValueType.NUMBER;
        valueTypes['5'] = ValueType.NUMBER;
        valueTypes['6'] = ValueType.NUMBER;
        valueTypes['7'] = ValueType.NUMBER;
        valueTypes['8'] = ValueType.NUMBER;
        valueTypes['9'] = ValueType.NUMBER;
        valueTypes['t'] = ValueType.BOOLEAN;
        valueTypes['f'] = ValueType.BOOLEAN;
        valueTypes['n'] = ValueType.NULL;
        valueTypes['['] = ValueType.ARRAY;
        valueTypes['{'] = ValueType.OBJECT;
    }

    private JsonIterator(byte[] buf, int head, int tail) {
        this.buf = buf;
        this.head = head;
        this.tail = tail;
    }

    public static JsonIterator parse(byte[] buf) {
        return new JsonIterator(buf, 0, buf.length);
    }

    public static JsonIterator parse(byte[] buf, int head, int tail) {
        return new JsonIterator(buf, head, tail);
    }

    public static JsonIterator parse(String str) {
        return parse(str.getBytes(StandardCharsets.UTF_8));
    }

    public static JsonIterator parse(Slice slice) {
        return new JsonIterator(slice.data(), slice.head(), slice.tail());
    }

    public void reset(byte[] buf) {
        this.buf = buf;
        this.head = 0;
        this.tail = buf.length;
        resetState();
    }

    public void reset(byte[] buf, int head, int tail) {
        this.buf = buf;
        this.head = head;
        this.tail = tail;
        resetState();
    }

    public void reset(Slice value) {
        this.buf = value.data();
        this.head = value.head();
        this.tail = value.tail();
        resetState();
    }

    private void resetState() {
        depth = 0;
        closed = false;
        error = null;
    }

    public void close() throws IOException {
        if (!closed) {
            // Only check for trailing data if the iterator hasn't already failed.
            if (error == null && head != buf.length) {
                while (head != buf.length) {
                    switch (buf[head++]) {
                        case ' ', '\n', '\t', '\r':
                            break;
                        default:
                            throw reportError("Found extraneous content");
                    }
                }
            }
        }
    }

    void unreadByte() {
        if (head == 0) {
            throw reportError("Unread too many bytes");
        }
        head--;
    }

    JsonException reportError(String msg) {
        int peekStart = head - 10;
        if (peekStart < 0) {
            peekStart = 0;
        }
        int peekSize = head - peekStart;
        if (head > tail) {
            peekSize = tail - peekStart;
        }
        String peek = new String(buf, peekStart, peekSize, StandardCharsets.UTF_8);

        error = peek.isEmpty()
            ? new JsonException(msg + "; at character " + head)
            : new JsonException(msg + "; at character " + head + " near '" + peek + '\'');

        return error;
    }

    public void readNull() throws IOException {
        try {
            if (nextToken(this) != 'n') {
                throw reportError("Expected null value");
            }
            if (nextToken(this) != 'u') {
                throw reportError("Expected null value");
            }
            if (nextToken(this) != 'l') {
                throw reportError("Expected null value");
            }
            if (nextToken(this) != 'l') {
                throw reportError("Expected null value");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Invalid null value");
        }
    }

    public boolean readBoolean() throws IOException {
        try {
            return switch (nextToken(this)) {
                case 't' -> {
                    IterImpl.readTrueRemainder(this);
                    yield true;
                }
                case 'f' -> {
                    IterImpl.readFalseRemainder(this);
                    yield false;
                }
                default -> throw reportError("Expected boolean true or false value");
            };
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Invalid boolean value");
        }
    }

    public short readShort() throws IOException {
        int v = readInt();
        if (Short.MIN_VALUE <= v && v <= Short.MAX_VALUE) {
            return (short) v;
        } else {
            throw reportError("Short overflow: " + v);
        }
    }

    public int readInt() throws IOException {
        return IterImplNumber.readInt(this);
    }

    public long readLong() throws IOException {
        return IterImplNumber.readLong(this);
    }

    public Number readNumber() throws IOException {
        return IterImplForStreaming.parseJsonNumber(this);
    }

    public String readString() throws IOException {
        return IterImplString.readString(this);
    }

    public Slice readStringAsSlice() throws IOException {
        return IterImpl.readSlice(this);
    }

    private void descend() {
        if (++depth > MAX_DEPTH) {
            throw reportError("JSON is too deeply nested");
        }
    }

    private void ascend() {
        depth--;
    }

    public boolean startReadArray() throws IOException {
        try {
            byte c = nextToken(this);
            return switch (c) {
                case 'n' -> {
                    unreadByte();
                    readNull();
                    yield false;
                }
                case '[' -> {
                    if (nextToken(this) == ']') {
                        yield false;
                    }
                    descend();
                    unreadByte();
                    yield true;
                }
                default -> throw reportError("Expected '[' or 'null', but found: " + (char) c);
            };
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Incomplete array");
        }
    }

    public boolean readNextArrayValue() throws IOException {
        try {
            byte c = nextToken(this);
            if (c == ']') {
                ascend();
                return false;
            } else if (c == ',') {
                return true;
            } else {
                throw reportError("Expected ',' or ']'");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Unclosed array");
        }
    }

    public boolean startReadObject() throws IOException {
        try {
            byte c = nextToken(this);
            return switch (c) {
                case 'n' -> {
                    unreadByte();
                    readNull();
                    yield false;
                }
                case '{' -> {
                    yield switch (nextToken(this)) {
                        case '"' -> {
                            descend();
                            unreadByte();
                            yield true;
                        }
                        case '}' -> false;
                        default -> throw reportError("Expected \" after '{'");
                    };
                }
                default -> throw reportError("Expected '{' or 'null', but found: " + (char) c);
            };
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Incomplete object");
        }
    }

    public String readObjectKey() throws IOException {
        try {
            String field = readString();
            if (field == null) {
                throw reportError("Expected object key, but found null");
            }
            if (nextToken(this) != ':') {
                throw reportError("Expected ':'");
            }
            return field;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Incomplete object");
        }
    }

    public Slice readObjectKeySlice() throws IOException {
        try {
            Slice field = readStringAsSlice();
            if (field == null) {
                throw reportError("Expected object key, but found null");
            }
            if (nextToken(this) != ':') {
                throw reportError("Expected ':'");
            }
            return field;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Incomplete object");
        }
    }

    public boolean keepReadingObject() throws IOException {
        try {
            byte c = nextToken(this);
            return switch (c) {
                case ',' -> true;
                case '}' -> {
                    ascend();
                    yield false;
                }
                default -> throw reportError("Expected ',' or '}'");
            };
        } catch (ArrayIndexOutOfBoundsException e) {
            throw reportError("Incomplete object");
        }
    }

    public float readFloat() throws IOException {
        return IterImplNumber.readFloat(this);
    }

    public double readDouble() throws IOException {
        return IterImplNumber.readDouble(this);
    }

    public BigDecimal readBigDecimal() throws IOException {
        // skip whitespace by read next
        ValueType valueType = whatIsNext();
        if (valueType == ValueType.NULL) {
            skip();
            return null;
        }
        if (valueType != ValueType.NUMBER) {
            throw reportError("Expected a number");
        }
        var number = IterImplForStreaming.parseJsonNumber(this);
        if (number instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (number instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        return (BigDecimal) number;
    }

    public BigInteger readBigInteger() throws IOException {
        // skip whitespace by read next
        ValueType valueType = whatIsNext();
        if (valueType == ValueType.NULL) {
            skip();
            return null;
        }
        if (valueType != ValueType.NUMBER) {
            throw reportError("Expected a number");
        }
        var number = IterImplForStreaming.parseJsonNumber(this);
        if (number instanceof Integer i) {
            return BigInteger.valueOf(i);
        }
        if (number instanceof Long l) {
            return BigInteger.valueOf(l);
        }
        return ((BigDecimal) number).toBigInteger();
    }

    public ValueType whatIsNext() throws IOException {
        try {
            ValueType valueType = valueTypes[nextToken(this)];
            unreadByte();
            return valueType;
        } catch (ArrayIndexOutOfBoundsException e) {
            return ValueType.INVALID;
        }
    }

    public void skip() throws IOException {
        IterImplSkip.skip(this);
    }
}
