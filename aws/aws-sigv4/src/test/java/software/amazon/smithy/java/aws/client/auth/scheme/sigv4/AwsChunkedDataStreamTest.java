/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

class AwsChunkedDataStreamTest {

    @Test
    void writeToProducesAwsChunkedFraming() throws Exception {
        // 1024 bytes of repeating "abcdefgh" — enough to need multi-char hex size.
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + (i % 8));
        }
        var inner = DataStream.ofBytes(payload, "application/octet-stream");
        var chunked = new AwsChunkedDataStream(inner);

        var out = new ByteArrayOutputStream();
        chunked.writeTo(out);
        byte[] encoded = out.toByteArray();

        // Frame format: 400\r\n<1024 bytes>\r\n0\r\nx-amz-checksum-crc32:<base64>\r\n\r\n
        var crc = new CRC32();
        crc.update(payload);
        long v = crc.getValue();
        byte[] crcBytes = {
                (byte) ((v >>> 24) & 0xff),
                (byte) ((v >>> 16) & 0xff),
                (byte) ((v >>> 8) & 0xff),
                (byte) (v & 0xff)};
        String crcB64 = Base64.getEncoder().encodeToString(crcBytes);

        String expected = "400\r\n"
                + new String(payload, StandardCharsets.US_ASCII)
                + "\r\n0\r\nx-amz-checksum-crc32:" + crcB64 + "\r\n\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    void contentLengthMatchesActualEncoding() throws Exception {
        for (int size : new int[] {1, 15, 16, 255, 256, 1024, 16384, 262144}) {
            byte[] payload = new byte[size];
            var chunked = new AwsChunkedDataStream(DataStream.ofBytes(payload, null));

            var out = new ByteArrayOutputStream();
            chunked.writeTo(out);

            assertThat("size=" + size, (long) out.size(), equalTo(chunked.contentLength()));
        }
    }

    @Test
    void subscribeProducesSameBytesAsWriteTo() throws Exception {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(payload, null));

        var sync = new ByteArrayOutputStream();
        chunked.writeTo(sync);

        var collected = new ByteArrayOutputStream();
        var done = new CompletableFuture<Void>();
        chunked.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buf) {
                byte[] tmp = new byte[buf.remaining()];
                buf.duplicate().get(tmp);
                try {
                    collected.write(tmp);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                done.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get();

        assertArrayEquals(sync.toByteArray(), collected.toByteArray());
    }

    @Test
    void replayableProducesIdenticalEncodingAcrossCalls() throws Exception {
        byte[] payload = "stable".getBytes(StandardCharsets.UTF_8);
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(payload, null));

        var first = new ByteArrayOutputStream();
        chunked.writeTo(first);
        var second = new ByteArrayOutputStream();
        chunked.writeTo(second);

        assertArrayEquals(first.toByteArray(), second.toByteArray());
        assertThat(chunked.isReplayable(), is(true));
    }

    @Test
    void emptyBodyComputesAllZeroCrc() throws Exception {
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(new byte[0], null));
        var out = new ByteArrayOutputStream();
        chunked.writeTo(out);
        // CRC32 of empty bytes is 0 → base64("\0\0\0\0") == "AAAAAA==".
        String expected = "0\r\n\r\n0\r\nx-amz-checksum-crc32:AAAAAA==\r\n\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.US_ASCII), out.toByteArray());
    }

    @Test
    void rejectsNonReplayableInput() {
        var nonReplayable = DataStream.ofInputStream(new ByteArrayInputStream("x".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> new AwsChunkedDataStream(nonReplayable));
    }

    @Test
    void decodedLengthReflectsInnerLength() {
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(new byte[42], null));
        assertThat(chunked.decodedLength(), equalTo(42L));
    }

    @Test
    void asInputStreamMirrorsWriteTo() throws Exception {
        byte[] payload = new byte[256];
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(payload, null));

        var fromStream = chunked.asInputStream().readAllBytes();
        var fromWriteTo = new ByteArrayOutputStream();
        chunked.writeTo(fromWriteTo);

        assertArrayEquals(fromWriteTo.toByteArray(), fromStream);
    }

    @Test
    void singleSubscriberCannotBeRequestedTwice() {
        var chunked = new AwsChunkedDataStream(DataStream.ofBytes(new byte[4], null));
        var calls = new AtomicReference<>(new ArrayList<String>());
        chunked.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription sub;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                sub = s;
                s.request(Long.MAX_VALUE);
                // Second request after first should be a no-op.
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                calls.get().add("next");
            }

            @Override
            public void onError(Throwable throwable) {
                calls.get().add("error");
            }

            @Override
            public void onComplete() {
                calls.get().add("complete");
            }
        });
        // Frames: header + body + footer + complete = 3 onNext + 1 onComplete.
        assertThat(calls.get(), equalTo(List.of("next", "next", "next", "complete")));
    }
}
