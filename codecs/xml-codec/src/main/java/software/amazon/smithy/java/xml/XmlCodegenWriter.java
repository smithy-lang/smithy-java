/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * XML output state for generated codecs.
 *
 * <p>Generated code knows every tag it will ever write, so the tags arrive here as precomputed byte
 * runs rather than as schemas to resolve. That removes the interpreted serializer's whole notion of a
 * deferred {@code '>'}: {@code pendingClose} exists only because the dispatch path discovers whether a
 * value has attributes after it has already written the opening tag. A generated writer decided that
 * at generation time, so it bakes {@code "<Name>"} when there are no attributes and
 * {@code "<Name"} + attributes + {@code ">"} when there are.
 *
 * <p>The fused {@code element*} methods take the opening and closing runs alongside the value so a
 * scalar member costs one call and one capacity check instead of three of each.
 *
 * <p>Every method reserves its worst case before writing a byte. The escaped-text bounds come from
 * {@link XmlWriteUtils}, which accounts for multi-byte UTF-8 and surrogate pairs.
 */
final class XmlCodegenWriter {
    private static final int DEFAULT_CAPACITY = 4096;
    private static final int MAX_CACHEABLE_CAPACITY = DEFAULT_CAPACITY * 4;

    /**
     * Widest {@code Instant.toString()} is {@code +1000000000-12-31T23:59:59.999999999Z} at 37 bytes.
     */
    private static final int MAX_DATE_TIME_BYTES = 40;

    private static final byte[] DIGIT_PAIRS = buildDigitPairs();

    private static final VarHandle LONG_VIEW =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
    private static final VarHandle INT_VIEW =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());

    private static final StripedPool<XmlCodegenWriter, XmlSettings> POOL = new WriterPool();

    private byte[] bytes = new byte[DEFAULT_CAPACITY];
    private int position;
    private OutputStream sink;

    private XmlCodegenWriter() {}

    /**
     * Borrows a writer.
     *
     * <p>The settings are only a pool key here; everything they influence on the write side, including
     * the default namespace, is already baked into the generated tag constants.
     */
    static XmlCodegenWriter acquire(XmlSettings settings) {
        return POOL.acquire(settings);
    }

    static void release(XmlCodegenWriter writer) {
        POOL.release(writer);
    }

    void sink(OutputStream sink) {
        this.sink = sink;
    }

    /**
     * Copies out the encoded document.
     *
     * <p>The copy is deliberate. This writer goes back into a pool the instant the caller is done, and
     * a wrapped view of the pooled array would alias whatever the next acquirer writes.
     */
    ByteBuffer detach() {
        return ByteBuffer.wrap(Arrays.copyOf(bytes, position));
    }

    int size() {
        return position;
    }

    void flush() {
        if (sink == null || position == 0) {
            return;
        }
        try {
            sink.write(bytes, 0, position);
            position = 0;
            sink.flush();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    /** Writes a precomputed byte run: an opening tag, a closing tag, or a lone {@code '>'}. */
    void raw(byte[] token) {
        ensure(token.length);
        position = copyToken(token, position);
    }

    void emptyElement(byte[] open, byte[] close) {
        ensure(open.length + close.length);
        position = copyToken(close, copyToken(open, position));
    }

    void elementString(byte[] open, String value, byte[] close) {
        ensure(open.length + XmlWriteUtils.maxEscapedTextBytes(value) + close.length);
        position = copyToken(open, position);
        position = XmlWriteUtils.writeEscapedText(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementBoolean(byte[] open, boolean value, byte[] close) {
        ensure(open.length + 5 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeBoolean(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementByte(byte[] open, byte value, byte[] close) {
        ensure(open.length + 4 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeInt(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementShort(byte[] open, short value, byte[] close) {
        ensure(open.length + 6 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeInt(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementInteger(byte[] open, int value, byte[] close) {
        ensure(open.length + 11 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeInt(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementLong(byte[] open, long value, byte[] close) {
        ensure(open.length + 20 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeLong(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementFloat(byte[] open, float value, byte[] close) {
        ensure(open.length + 24 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeFloatFull(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementDouble(byte[] open, double value, byte[] close) {
        ensure(open.length + 24 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeDoubleFull(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementBigInteger(byte[] open, BigInteger value, byte[] close) {
        ensure(open.length + value.bitLength() / 3 + 2 + close.length);
        position = copyToken(open, position);
        position = NumberCodec.writeBigInteger(bytes, position, value);
        position = copyToken(close, position);
    }

    void elementBigDecimal(byte[] open, BigDecimal value, byte[] close) {
        String text = value.toString();
        ensure(open.length + text.length() + close.length);
        position = copyToken(open, position);
        position = writeAscii(bytes, position, text);
        position = copyToken(close, position);
    }

    void elementBlob(byte[] open, ByteBuffer value, byte[] close) {
        byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
        ensure(open.length + encoded.length + close.length);
        position = copyToken(open, position);
        System.arraycopy(encoded, 0, bytes, position, encoded.length);
        position += encoded.length;
        position = copyToken(close, position);
    }

    void elementTimestamp(byte[] open, Instant value, int format, byte[] close) {
        if (format == FORMAT_DATE_TIME) {
            ensure(open.length + MAX_DATE_TIME_BYTES + close.length);
            position = copyToken(open, position);
            position = writeDateTime(bytes, position, value);
            position = copyToken(close, position);
        } else {
            // http-date and epoch-seconds are rare in XML protocols and their interpreted
            // formatters have enough range and rounding behaviour of their own that
            // reimplementing them here would risk diverging bytes for no measurable gain.
            String text = formatter(format).writeString(value);
            ensure(open.length + text.length() + close.length);
            position = copyToken(open, position);
            position = writeAscii(bytes, position, text);
            position = copyToken(close, position);
        }
    }

    void attrString(byte[] prefix, String value) {
        ensure(prefix.length + XmlWriteUtils.maxEscapedAttributeBytes(value) + 1);
        position = copyToken(prefix, position);
        position = XmlWriteUtils.writeEscapedAttribute(bytes, position, value);
        bytes[position++] = '"';
    }

    void attrBoolean(byte[] prefix, boolean value) {
        ensure(prefix.length + 5 + 1);
        position = copyToken(prefix, position);
        position = NumberCodec.writeBoolean(bytes, position, value);
        bytes[position++] = '"';
    }

    void attrInteger(byte[] prefix, int value) {
        ensure(prefix.length + 11 + 1);
        position = copyToken(prefix, position);
        position = NumberCodec.writeInt(bytes, position, value);
        bytes[position++] = '"';
    }

    void attrLong(byte[] prefix, long value) {
        ensure(prefix.length + 20 + 1);
        position = copyToken(prefix, position);
        position = NumberCodec.writeLong(bytes, position, value);
        bytes[position++] = '"';
    }

    /**
     * Attributes format floats with {@code writeFloat}, not the {@code writeFloatFull} used for element
     * text. That asymmetry is in the interpreted serializer; matching it keeps generated bytes identical.
     */
    void attrFloat(byte[] prefix, float value) {
        ensure(prefix.length + 24 + 1);
        position = copyToken(prefix, position);
        position = Float.isFinite(value)
                ? NumberCodec.writeFloat(bytes, position, value)
                : NumberCodec.writeNonFiniteFloat(bytes, position, value);
        bytes[position++] = '"';
    }

    void attrDouble(byte[] prefix, double value) {
        ensure(prefix.length + 24 + 1);
        position = copyToken(prefix, position);
        position = Double.isFinite(value)
                ? NumberCodec.writeDouble(bytes, position, value)
                : NumberCodec.writeNonFiniteDouble(bytes, position, value);
        bytes[position++] = '"';
    }

    void attrTimestamp(byte[] prefix, Instant value, int format) {
        if (format == FORMAT_DATE_TIME) {
            ensure(prefix.length + MAX_DATE_TIME_BYTES + 1);
            position = copyToken(prefix, position);
            position = writeDateTime(bytes, position, value);
            bytes[position++] = '"';
        } else {
            // The interpreted path routes attribute timestamps through writeString, which escapes.
            // No format produces an escapable byte, but go through the same call for exactness.
            attrString(prefix, formatter(format).writeString(value));
        }
    }

    /** Timestamp format codes baked into generated code, so no trait lookup happens per value. */
    static final int FORMAT_EPOCH_SECONDS = 0;
    static final int FORMAT_DATE_TIME = 1;
    static final int FORMAT_HTTP_DATE = 2;

    private static TimestampFormatter formatter(int format) {
        return format == FORMAT_HTTP_DATE
                ? TimestampFormatter.Prelude.HTTP_DATE
                : TimestampFormatter.Prelude.EPOCH_SECONDS;
    }

    static int formatCode(TimestampFormatTrait.Format format) {
        return switch (format) {
            case HTTP_DATE -> FORMAT_HTTP_DATE;
            case EPOCH_SECONDS -> FORMAT_EPOCH_SECONDS;
            default -> FORMAT_DATE_TIME;
        };
    }

    /**
     * Writes an instant exactly as {@link Instant#toString()} does.
     *
     * <p>This cannot delegate to {@code TimestampCodec.writeIso8601}: that trims trailing zeros from the
     * fractional second, while {@code ISO_INSTANT} pads to three, six, or nine digits. {@code 0.1s}
     * prints as {@code .100} here and {@code .1} there, so substituting it would change the wire bytes
     * of every sub-second timestamp.
     */
    private static int writeDateTime(byte[] buf, int pos, Instant value) {
        long epochSecond = value.getEpochSecond();
        int nano = value.getNano();

        long epochDay = Math.floorDiv(epochSecond, 86400L);
        int secondOfDay = (int) Math.floorMod(epochSecond, 86400L);
        int hour = secondOfDay / 3600;
        int minute = (secondOfDay % 3600) / 60;
        int second = secondOfDay % 60;

        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));

        if (year < 0 || year > 9999) {
            // ISO_INSTANT signs and widens the year outside this range, and does so differently at the
            // extremes of Instant. Not worth reproducing; defer to the JDK.
            return writeAscii(buf, pos, value.toString());
        }

        int yearHi = year / 100;
        int yearLo = year - yearHi * 100;
        buf[pos] = DIGIT_PAIRS[yearHi * 2];
        buf[pos + 1] = DIGIT_PAIRS[yearHi * 2 + 1];
        buf[pos + 2] = DIGIT_PAIRS[yearLo * 2];
        buf[pos + 3] = DIGIT_PAIRS[yearLo * 2 + 1];
        buf[pos + 4] = '-';
        buf[pos + 5] = DIGIT_PAIRS[month * 2];
        buf[pos + 6] = DIGIT_PAIRS[month * 2 + 1];
        buf[pos + 7] = '-';
        buf[pos + 8] = DIGIT_PAIRS[day * 2];
        buf[pos + 9] = DIGIT_PAIRS[day * 2 + 1];
        buf[pos + 10] = 'T';
        buf[pos + 11] = DIGIT_PAIRS[hour * 2];
        buf[pos + 12] = DIGIT_PAIRS[hour * 2 + 1];
        buf[pos + 13] = ':';
        buf[pos + 14] = DIGIT_PAIRS[minute * 2];
        buf[pos + 15] = DIGIT_PAIRS[minute * 2 + 1];
        buf[pos + 16] = ':';
        buf[pos + 17] = DIGIT_PAIRS[second * 2];
        buf[pos + 18] = DIGIT_PAIRS[second * 2 + 1];
        pos += 19;

        if (nano != 0) {
            // ISO_INSTANT emits three, six, or nine fractional digits: never a partial group and never
            // stripped below a group boundary.
            buf[pos++] = '.';
            pos = writeThreeDigits(buf, pos, nano / 1_000_000);
            int remainder = nano % 1_000_000;
            if (remainder != 0) {
                pos = writeThreeDigits(buf, pos, remainder / 1_000);
                int lo = remainder % 1_000;
                if (lo != 0) {
                    pos = writeThreeDigits(buf, pos, lo);
                }
            }
        }

        buf[pos++] = 'Z';
        return pos;
    }

    private static int writeThreeDigits(byte[] buf, int pos, int value) {
        buf[pos] = (byte) ('0' + value / 100);
        buf[pos + 1] = (byte) ('0' + (value / 10) % 10);
        buf[pos + 2] = (byte) ('0' + value % 10);
        return pos + 3;
    }

    @SuppressWarnings("deprecation")
    private static int writeAscii(byte[] buf, int pos, String value) {
        int length = value.length();
        value.getBytes(0, length, buf, pos);
        return pos + length;
    }

    /**
     * Copies a precomputed tag run, returning the new position.
     *
     * <p>Tags are almost always shorter than two words, and {@link System#arraycopy} for a handful of
     * bytes calls out to a stub. Two overlapping word stores replace that; because they overlap rather
     * than pad, nothing past the token is touched. The caller must already have reserved
     * {@code token.length} bytes.
     */
    private int copyToken(byte[] token, int pos) {
        int length = token.length;
        if (length <= Long.BYTES * 2) {
            if (length >= Long.BYTES) {
                int tail = length - Long.BYTES;
                LONG_VIEW.set(bytes, pos, (long) LONG_VIEW.get(token, 0));
                LONG_VIEW.set(bytes, pos + tail, (long) LONG_VIEW.get(token, tail));
                return pos + length;
            }
            if (length >= Integer.BYTES) {
                int tail = length - Integer.BYTES;
                INT_VIEW.set(bytes, pos, (int) INT_VIEW.get(token, 0));
                INT_VIEW.set(bytes, pos + tail, (int) INT_VIEW.get(token, tail));
                return pos + length;
            }
        }
        System.arraycopy(token, 0, bytes, pos, length);
        return pos + length;
    }

    private void ensure(int needed) {
        if (position + needed > bytes.length) {
            grow(needed);
        }
    }

    private void grow(int needed) {
        bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, position + needed));
    }

    private static byte[] buildDigitPairs() {
        byte[] pairs = new byte[200];
        for (int i = 0; i < 100; i++) {
            pairs[i * 2] = (byte) ('0' + i / 10);
            pairs[i * 2 + 1] = (byte) ('0' + i % 10);
        }
        return pairs;
    }

    private static final class WriterPool extends StripedPool<XmlCodegenWriter, XmlSettings> {
        @Override
        protected XmlCodegenWriter create(XmlSettings settings) {
            return new XmlCodegenWriter();
        }

        @Override
        protected void cleanup(XmlCodegenWriter writer) {
            writer.sink = null;
            writer.position = 0;
        }

        @Override
        protected boolean canPool(XmlCodegenWriter writer) {
            return writer.bytes != null;
        }

        @Override
        protected void prepareForPool(XmlCodegenWriter writer) {
            if (writer.bytes.length > MAX_CACHEABLE_CAPACITY) {
                writer.bytes = new byte[DEFAULT_CAPACITY];
            }
        }

        @Override
        protected boolean reset(XmlCodegenWriter writer, XmlSettings settings) {
            writer.position = 0;
            return true;
        }
    }
}
