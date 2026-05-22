/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Zero-copy body subscriber for known-length responses that fit in memory.
 *
 * <p>Per the {@link HttpResponse.BodySubscriber} contract: <i>"both the Lists and the ByteBuffers, once passed to
 * the subscriber, are no longer used by the HTTP Client."</i> That means we can hold direct references to the chunks
 * the JDK delivers and hand them to downstream code without copying. The completed {@link DataStream} keeps the chunks
 * intact and only stitches into a contiguous buffer if a caller specifically asks for one via {@code asByteBuffer()}.
 */
final class ZeroCopyBodySubscriber implements HttpResponse.BodySubscriber<DataStream> {
    private final String contentType;
    private final long contentLength;
    private final List<ByteBuffer> chunks;
    private final CompletableFuture<DataStream> body = new CompletableFuture<>();

    ZeroCopyBodySubscriber(HttpHeaders headers, long contentLength) {
        this.contentType = headers.firstValue("content-type").orElse(null);
        this.contentLength = contentLength;
        // Most responses arrive in 1–4 chunks for our small-body threshold; size the
        // backing array accordingly to skip the default-capacity grow-and-copy.
        this.chunks = new ArrayList<>(4);
    }

    @Override
    public CompletionStage<DataStream> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // Only used for known-length bodies bounded by the small-body threshold, so we can
        // safely drain everything without backpressure.
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        chunks.addAll(item);
    }

    @Override
    public void onError(Throwable throwable) {
        body.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        body.complete(DataStream.ofByteBuffers(chunks, contentLength, contentType));
    }
}
