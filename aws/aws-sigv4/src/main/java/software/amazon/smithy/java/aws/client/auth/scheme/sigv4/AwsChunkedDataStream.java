/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Wraps a {@link DataStream} so its bytes are emitted in {@code aws-chunked} content-encoding
 * with an inline CRC32 checksum trailer. Used to implement the unsigned-payload-trailer signing
 * variant per the flexible-checksums SEP §"Unsigned payload signing".
 *
 * <p>Wire format produced:
 *
 * <pre>{@code
 *  <hex-size>\r\n<chunk-bytes>\r\n
 *  0\r\n
 *  x-amz-checksum-crc32:<base64-crc>\r\n
 *  \r\n
 * }</pre>
 *
 * <p>The "trailer" is part of the body bytes — not an HTTP/1.1 transfer-encoding trailer — so no
 * special transport support is needed. The receiving service (S3) parses the trailer line out of
 * the body when {@code Content-Encoding: aws-chunked} and {@code x-amz-trailer: x-amz-checksum-*}
 * are set on the request.
 *
 * <p>This implementation only supports replayable, in-memory bodies (single-buffer). Streaming
 * bodies would emit multiple chunks; we punt on that until needed.
 */
public final class AwsChunkedDataStream implements DataStream {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] TERMINATOR = {'0', '\r', '\n'};
    private static final byte[] TRAILER_NAME = "x-amz-checksum-crc32:".getBytes(StandardCharsets.US_ASCII);

    private final DataStream inner;
    private final long encodedLength;
    private final long decodedLength;

    public AwsChunkedDataStream(DataStream inner) {
        if (!inner.isReplayable() || !inner.hasByteBuffer()) {
            throw new IllegalArgumentException("AwsChunkedDataStream requires a replayable, "
                    + "byte-buffer-backed inner stream; got " + inner.getClass().getSimpleName());
        }
        this.inner = inner;
        this.decodedLength = inner.contentLength();
        // Single chunk: <hexsize>\r\n<bytes>\r\n + 0\r\n + trailer-line\r\n + \r\n
        // CRC32 is 4 bytes → base64 is always 8 chars (with padding) for any 4-byte input.
        int hexSizeLen = hexLen(decodedLength);
        int trailerLineLen = TRAILER_NAME.length + 8 /* base64(crc32) */ + CRLF.length;
        this.encodedLength = (long) hexSizeLen + CRLF.length
                + decodedLength + CRLF.length
                + TERMINATOR.length
                + trailerLineLen
                + CRLF.length;
    }

    public long decodedLength() {
        return decodedLength;
    }

    @Override
    public long contentLength() {
        return encodedLength;
    }

    @Override
    public String contentType() {
        // aws-chunked is a content-encoding, not a content-type — preserve the inner type.
        return inner.contentType();
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return inner.isAvailable();
    }

    @Override
    public InputStream asInputStream() {
        // Materialize the encoded bytes once. Used by tests / sync transports; the publisher path
        // (subscribe) is what JavaHttpClientTransport uses on the hot benchmark path and avoids
        // this allocation.
        var out = new ByteArrayOutputStream((int) encodedLength);
        try {
            writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        var buf = inner.asByteBuffer();
        long size = buf.remaining();
        var crc = new CRC32();
        crc.update(buf.duplicate());
        out.write(hexBytes(size));
        out.write(CRLF);
        if (buf.hasArray()) {
            out.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        } else {
            byte[] tmp = new byte[buf.remaining()];
            buf.duplicate().get(tmp);
            out.write(tmp);
        }
        out.write(CRLF);
        out.write(TERMINATOR);
        out.write(TRAILER_NAME);
        out.write(crc32Base64(crc));
        out.write(CRLF);
        out.write(CRLF);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Subscription(subscriber));
    }

    private final class Subscription implements Flow.Subscription {
        private final AtomicBoolean done = new AtomicBoolean();
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        Subscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0 || !done.compareAndSet(false, true)) {
                return;
            }
            try {
                var body = inner.asByteBuffer();
                var crc = new CRC32();
                crc.update(body.duplicate());

                // Frame: <hex-size>\r\n
                ByteBuffer header = ByteBuffer.wrap(joinHeader(body.remaining()));
                subscriber.onNext(header);

                // Frame: <chunk bytes>
                subscriber.onNext(body.duplicate());

                // Frame: \r\n then 0\r\n then trailer line then \r\n
                subscriber.onNext(ByteBuffer.wrap(joinFooter(crc)));

                subscriber.onComplete();
            } catch (RuntimeException e) {
                subscriber.onError(e);
            }
        }

        @Override
        public void cancel() {
            done.set(true);
        }
    }

    private static byte[] joinHeader(int chunkSize) {
        byte[] hex = hexBytes(chunkSize);
        byte[] out = new byte[hex.length + CRLF.length];
        System.arraycopy(hex, 0, out, 0, hex.length);
        System.arraycopy(CRLF, 0, out, hex.length, CRLF.length);
        return out;
    }

    private static byte[] joinFooter(CRC32 crc) {
        byte[] crcB64 = crc32Base64(crc);
        // \r\n + 0\r\n + trailer-name + base64-crc + \r\n + \r\n
        int len = CRLF.length + TERMINATOR.length + TRAILER_NAME.length + crcB64.length + CRLF.length + CRLF.length;
        byte[] out = new byte[len];
        int p = 0;
        System.arraycopy(CRLF, 0, out, p, CRLF.length);
        p += CRLF.length;
        System.arraycopy(TERMINATOR, 0, out, p, TERMINATOR.length);
        p += TERMINATOR.length;
        System.arraycopy(TRAILER_NAME, 0, out, p, TRAILER_NAME.length);
        p += TRAILER_NAME.length;
        System.arraycopy(crcB64, 0, out, p, crcB64.length);
        p += crcB64.length;
        System.arraycopy(CRLF, 0, out, p, CRLF.length);
        p += CRLF.length;
        System.arraycopy(CRLF, 0, out, p, CRLF.length);
        return out;
    }

    private static byte[] crc32Base64(CRC32 crc) {
        long value = crc.getValue();
        byte[] crcBytes = {
                (byte) ((value >>> 24) & 0xff),
                (byte) ((value >>> 16) & 0xff),
                (byte) ((value >>> 8) & 0xff),
                (byte) (value & 0xff)};
        return Base64.getEncoder().encode(crcBytes);
    }

    private static byte[] hexBytes(long size) {
        return Long.toHexString(size).getBytes(StandardCharsets.US_ASCII);
    }

    private static int hexLen(long size) {
        return Long.toHexString(size).length();
    }

    @Override
    public void close() {
        inner.close();
    }
}
