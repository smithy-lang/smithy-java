/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Small-body subscriber for known-length responses that fit in memory cheaply.
 *
 * <p>Allocates one array up front and completes with a replayable {@link DataStream}.
 */
final class JavaHttpClientSmallBodySubscriber implements HttpResponse.BodySubscriber<DataStream> {
    private final String contentType;
    private final byte[] bytes;
    private int position;
    private final CompletableFuture<DataStream> body = new CompletableFuture<>();

    JavaHttpClientSmallBodySubscriber(HttpHeaders headers, int contentLength) {
        this.contentType = headers.firstValue("content-type").orElse(null);
        this.bytes = new byte[Math.max(contentLength, 0)];
    }

    @Override
    public CompletionStage<DataStream> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // This path is only used for known small bodies, so the full response is already
        // bounded by the fast-path threshold and can be aggregated safely in memory.
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
        byte[] result = position == bytes.length ? bytes : Arrays.copyOf(bytes, position);
        body.complete(DataStream.ofBytes(result, contentType));
    }
}
