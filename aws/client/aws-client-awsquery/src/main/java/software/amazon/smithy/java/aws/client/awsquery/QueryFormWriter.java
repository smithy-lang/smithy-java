/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Query output state driven by generated codecs.
 *
 * <p>This is the whole contract between {@link QueryRuntimeCodegenBackend} and the runtime, so it is
 * deliberately narrow. A generated codec knows every static parameter name at emit time, which is
 * the difference that matters: where {@link QueryFormSerializer} rebuilds
 * {@code &Outer.Inner.Name=} one schema lookup and one prefix push at a time, a generated codec
 * hands over a single constant {@code byte[]} holding the entire static path and this class copies
 * it in one go. Only the parts that genuinely vary at runtime -- collection indices and map keys --
 * go through the prefix stack.
 *
 * <p>Position and value are separate calls. A position call reserves and writes only the parameter
 * name; each {@code value*} call reserves its own worst case. That is one extra capacity check per
 * parameter compared with the interpreted serializer's single fused reservation, and it is what makes
 * the value writers unable to overrun the buffer no matter what the caller passes.
 */
final class QueryFormWriter extends QueryFormOutput {
    /** Digits in the largest {@code int}, plus a byte of slack. */
    private static final int MAX_INDEX_DIGITS = 11;

    private static final StripedPool<QueryFormWriter, Void> POOL = new WriterPool();

    private QueryFormWriter() {}

    static QueryFormWriter acquire(String action, String version) {
        QueryFormWriter writer = POOL.acquire(null);
        writer.appendHeader(action, version);
        return writer;
    }

    static void release(QueryFormWriter writer) {
        POOL.release(writer);
    }

    ByteBuffer detach() {
        return copyOut();
    }

    /**
     * Writes {@code &<prefix>.<tail>}, where {@code tail} is a constant path ending in {@code '='}.
     *
     * <p>{@code tail} is every static segment between the innermost runtime prefix and the value, so
     * a parameter three structures deep costs one {@code arraycopy} rather than three prefix pushes.
     */
    void param(byte[] tail) {
        ensureCapacity(1 + prefixLen + 1 + tail.length);
        buf[pos++] = '&';
        if (prefixLen > 0) {
            System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
            pos += prefixLen;
            buf[pos++] = '.';
        }
        System.arraycopy(tail, 0, buf, pos, tail.length);
        pos += tail.length;
    }

    /**
     * Writes {@code &<prefix>=}, the parameter whose name is exactly the current runtime prefix.
     *
     * <p>An empty AWS Query list still emits its own name with no value. When the list's name is
     * already on the prefix stack there is no static tail left to pass to {@link #param}, and
     * {@code param} would insert the separator it always puts before a tail.
     */
    void paramEmpty() {
        ensureCapacity(1 + prefixLen + 1);
        buf[pos++] = '&';
        System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
        pos += prefixLen;
        buf[pos++] = '=';
    }

    /**
     * Writes {@code &<prefix>.<head><index + 1>=} for a collection element that holds a value
     * directly.
     *
     * <p>{@code head} carries its own trailing {@code '.'}. Query indices are one-based, and the
     * increment happens here because {@code index} is the emitted loop counter.
     */
    void paramAt(byte[] head, int index) {
        ensureCapacity(1 + prefixLen + 1 + head.length + MAX_INDEX_DIGITS + 1);
        buf[pos++] = '&';
        if (prefixLen > 0) {
            System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
            pos += prefixLen;
            buf[pos++] = '.';
        }
        System.arraycopy(head, 0, buf, pos, head.length);
        pos += head.length;
        pos = NumberCodec.writeInt(buf, pos, index + 1);
        buf[pos++] = '=';
    }

    /**
     * Pushes {@code <head><index + 1>} onto the prefix stack for a collection element that holds a
     * structure, list, or map, so the element's own parameters can be folded relative to it.
     */
    void pushAt(byte[] head, int index) {
        pushPrefixIndexed(head, index + 1);
    }

    /**
     * Pushes an entire static path onto the prefix stack.
     *
     * <p>Only used where folding has to stop: a self-recursive structure has no finite set of static
     * paths, so past a depth bound the generated codec pushes the path it has accumulated so far and
     * calls the unfolded method for that shape.
     */
    void pushPath(byte[] path) {
        pushPrefix(path);
    }

    void popPath() {
        popPrefix();
    }

    void valueBoolean(boolean value) {
        ensureCapacity(5);
        pos = NumberCodec.writeBoolean(buf, pos, value);
    }

    void valueInt(int value) {
        ensureCapacity(11);
        pos = NumberCodec.writeInt(buf, pos, value);
    }

    void valueLong(long value) {
        ensureCapacity(20);
        pos = NumberCodec.writeLong(buf, pos, value);
    }

    void valueFloat(float value) {
        if (Float.isFinite(value)) {
            ensureCapacity(15);
            pos = NumberCodec.writeFloat(buf, pos, value);
        } else {
            ensureCapacity(9);
            pos = NumberCodec.writeNonFiniteFloat(buf, pos, value);
        }
    }

    void valueDouble(double value) {
        if (Double.isFinite(value)) {
            ensureCapacity(25);
            pos = NumberCodec.writeDouble(buf, pos, value);
        } else {
            ensureCapacity(9);
            pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
        }
    }

    void valueBigInteger(BigInteger value) {
        ensureCapacity(maxBigIntegerBytes(value));
        pos = NumberCodec.writeBigInteger(buf, pos, value);
    }

    void valueBigDecimal(BigDecimal value) {
        ensureCapacity(NumberCodec.maxBigDecimalLength(value));
        pos = NumberCodec.writeBigDecimal(buf, pos, value);
    }

    void valueString(String value) {
        ensureCapacity(value.length() * 3);
        appendUrlEncoded(value);
    }

    void valueBlob(ByteBuffer value) {
        byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
        ensureCapacity(encoded.length * 3);
        appendUrlEncodedBytes(encoded, encoded.length);
    }

    void valueIso8601(Instant value) {
        ensureCapacity(30);
        pos = TimestampCodec.writeIso8601(buf, pos, value);
    }

    void valueEpochSeconds(Instant value) {
        ensureCapacity(30);
        pos = TimestampCodec.writeEpochSeconds(buf, pos, value.getEpochSecond(), value.getNano());
    }

    void valueHttpDate(Instant value) {
        ensureCapacity(MAX_HTTP_DATE_BYTES);
        appendHttpDate(value);
    }

    private static final class WriterPool extends StripedPool<QueryFormWriter, Void> {
        @Override
        protected QueryFormWriter create(Void context) {
            return new QueryFormWriter();
        }

        @Override
        protected void cleanup(QueryFormWriter writer) {
            writer.resetOutput();
        }

        @Override
        protected boolean canPool(QueryFormWriter writer) {
            return true;
        }

        @Override
        protected void prepareForPool(QueryFormWriter writer) {
            if (writer.buf.length > MAX_CACHEABLE_BUF) {
                writer.buf = new byte[DEFAULT_BUF_SIZE];
            }
        }

        @Override
        protected boolean reset(QueryFormWriter writer, Void context) {
            return true;
        }
    }
}
