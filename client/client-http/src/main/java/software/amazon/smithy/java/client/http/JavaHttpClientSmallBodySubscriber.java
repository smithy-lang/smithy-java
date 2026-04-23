/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Response body subscriber optimized for small responses with a known {@code Content-Length}.
 *
 * <p>Chosen by {@link JavaHttpClientTransport}'s body handler when the response's declared
 * content length is non-negative and within the small-body threshold. It allocates the exact
 * final {@code byte[]} up front (sized from Content-Length), copies each arriving chunk into
 * that array, and completes with a {@link DataStream#ofBytes(byte[], String)} when the stream
 * ends.
 *
 * <p>Rationale: for small bodies the in-memory round-trip through a {@code byte[]} is cheaper
 * than the producer/consumer hand-off machinery of
 * {@link JavaHttpClientStreamingDataStream}. The caller also gets a <em>replayable</em>,
 * known-length {@link DataStream}, which downstream consumers (JSON codecs, signing plugins,
 * etc.) can read multiple times without buffering.
 *
 * <p>Correctness notes:
 * <ul>
 *   <li>Content-Length is advisory. If the server sends fewer bytes than advertised, the
 *       array is copied to a shorter one in {@link #onComplete}. If the server sends more,
 *       {@code ByteBuffer.get} will fail with {@link java.nio.BufferOverflowException}
 *       propagated via {@link #onError}. This matches the JDK HttpClient's own behavior for
 *       mismatched content lengths.</li>
 *   <li>Threading: all callbacks are invoked on the JDK HttpClient's executor thread, in
 *       subscription order, so no synchronization is needed between {@code onSubscribe},
 *       {@code onNext}, {@code onError}, and {@code onComplete}.</li>
 * </ul>
 */
final class JavaHttpClientSmallBodySubscriber implements HttpResponse.BodySubscriber<DataStream> {
    private final String contentType;
    private final byte[] bytes;
    private int position;
    private final CompletableFuture<DataStream> body = new CompletableFuture<>();

    JavaHttpClientSmallBodySubscriber(java.net.http.HttpHeaders headers, int contentLength) {
        this.contentType = headers.firstValue("content-type").orElse(null);
        // Math.max guards against a negative Content-Length header (malformed server).
        this.bytes = new byte[Math.max(contentLength, 0)];
    }

    @Override
    public CompletionStage<DataStream> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // Request everything at once; the JDK HttpClient implements Flow backpressure at the
        // socket level, so signalling Long.MAX_VALUE here does not actually cause unbounded
        // memory use — the wire only delivers what fits in the advertised content length.
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        for (ByteBuffer buffer : item) {
            int remaining = buffer.remaining();
            buffer.get(bytes, position, remaining);
            position += remaining;
        }
    }

    @Override
    public void onError(Throwable throwable) {
        body.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        // If the server sent less than Content-Length, hand out a right-sized array rather
        // than exposing uninitialized tail bytes to the caller.
        byte[] result = position == bytes.length ? bytes : Arrays.copyOf(bytes, position);
        body.complete(DataStream.ofBytes(result, contentType));
    }
}
