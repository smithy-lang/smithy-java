/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.ForwardingPooledDataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;

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
 * <p>Supports only replayable, in-memory bodies encoded as one chunk.
 * A {@link PooledDataStream} wrapper forwards lifecycle methods to the lease.
 * {@code writeTo} and {@link #forEachBuffer} read leased storage directly.
 * Reads after release throw before emitting bytes.
 */
public sealed class AwsChunkedDataStream implements DataStream {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] TERMINATOR = {'0', '\r', '\n'};
    private static final byte[] TRAILER_NAME = "x-amz-checksum-crc32:".getBytes(StandardCharsets.US_ASCII);
    // CRC32 is 4 bytes, so its base64 form is always 8 chars.
    private static final int FOOTER_LENGTH = CRLF.length + TERMINATOR.length
            + TRAILER_NAME.length
            + 8
            + CRLF.length
            + CRLF.length;

    private final DataStream inner;
    private final byte[] header;
    private final long encodedLength;
    private final long decodedLength;
    private byte[] footer;
    private byte[] encoded;

    public static AwsChunkedDataStream of(DataStream inner) {
        return inner instanceof PooledDataStream pooled ? new Pooled(pooled) : new AwsChunkedDataStream(inner);
    }

    private AwsChunkedDataStream(DataStream inner) {
        if (!inner.isReplayable() || !inner.hasByteBuffer()) {
            throw new IllegalArgumentException("AwsChunkedDataStream requires a replayable, "
                    + "byte-buffer-backed inner stream; got " + inner.getClass().getSimpleName());
        }
        this.inner = inner;
        this.decodedLength = inner.contentLength();
        this.header = header(decodedLength);
        this.encodedLength = header.length + decodedLength + FOOTER_LENGTH;
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
    public void writeTo(OutputStream out) throws IOException {
        checkAvailable();
        byte[] footer = footer();
        out.write(header);
        inner.writeTo(out);
        out.write(footer);
    }

    @Override
    public void writeTo(WritableByteChannel channel) throws IOException {
        checkAvailable();
        byte[] footer = footer();
        writeFully(channel, ByteBuffer.wrap(header));
        inner.writeTo(channel);
        writeFully(channel, ByteBuffer.wrap(footer));
    }

    @Override
    public void forEachBuffer(Consumer<ByteBuffer> action) {
        checkAvailable();
        byte[] footer = footer();
        action.accept(ByteBuffer.wrap(header));
        inner.forEachBuffer(action);
        action.accept(ByteBuffer.wrap(footer));
    }

    @Override
    public ByteBuffer asByteBuffer() {
        checkAvailable();
        return ByteBuffer.wrap(encoded());
    }

    @Override
    public InputStream asInputStream() {
        checkAvailable();
        return new ByteArrayInputStream(encoded());
    }

    /** Published buffers share storage with the cached encoded body or the wrapped stream. */
    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        checkAvailable();
        byte[] encoded = this.encoded;
        if (encoded != null) {
            DataStream.ofByteBuffer(ByteBuffer.wrap(encoded), contentType()).subscribe(subscriber);
            return;
        }
        byte[] footer = footer();
        var frames = List.of(ByteBuffer.wrap(header), inner.asByteBuffer(), ByteBuffer.wrap(footer));
        DataStream.ofByteBuffers(frames, encodedLength, contentType()).subscribe(subscriber);
    }

    @Override
    public void close() {
        inner.close();
    }

    // Check availability before every read, including cached reads, to prevent output after release.
    private void checkAvailable() {
        if (!inner.isAvailable()) {
            throw new IllegalStateException("This aws-chunked body wraps a pooled request body that was released "
                    + "when its call completed and cannot be read again.");
        }
    }

    private byte[] footer() {
        byte[] bytes = footer;
        if (bytes == null) {
            var crc = new CRC32();
            inner.forEachBuffer(crc::update);
            bytes = footer = footer(crc);
        }
        return bytes;
    }

    private byte[] encoded() {
        byte[] bytes = encoded;
        if (bytes == null) {
            bytes = encoded = encode();
        }
        return bytes;
    }

    private byte[] encode() {
        byte[] footer = footer();
        ByteBuffer dst = ByteBuffer.allocate((int) encodedLength);
        dst.put(header);
        inner.forEachBuffer(dst::put);
        dst.put(footer);
        return dst.array();
    }

    private static void writeFully(WritableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static byte[] header(long size) {
        byte[] hex = Long.toHexString(size).getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(hex.length + CRLF.length).put(hex).put(CRLF).array();
    }

    private static byte[] footer(CRC32 crc) {
        byte[] crcB64 = Base64.getEncoder().encode(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return ByteBuffer.allocate(FOOTER_LENGTH)
                .put(CRLF)
                .put(TERMINATOR)
                .put(TRAILER_NAME)
                .put(crcB64)
                .put(CRLF)
                .put(CRLF)
                .array();
    }

    private static final class Pooled extends AwsChunkedDataStream implements ForwardingPooledDataStream {
        Pooled(PooledDataStream lease) {
            super(lease);
        }

        @Override
        public PooledDataStream lease() {
            return (PooledDataStream) super.inner;
        }

        @Override
        public boolean escaped() {
            return lease().escaped() || super.encoded != null;
        }

        // A publisher can outlive the lease. Publish a private copy to keep its buffers valid after release.
        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            DataStream.ofByteBuffer(asByteBuffer(), contentType()).subscribe(subscriber);
        }

        @Override
        public DataStream copy() {
            super.checkAvailable();
            byte[] encoded = super.encoded;
            return DataStream.ofBytes(encoded != null ? encoded : super.encode(), contentType());
        }
    }
}
