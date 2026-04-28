/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Response body subscriber used for streaming (large or unknown-length) responses.
 *
 * <p>Chosen by {@link JavaHttpClientTransport}'s body handler when the small-body fast path
 * ({@link JavaHttpClientSmallBodySubscriber}) does not apply — i.e., when {@code Content-Length}
 * is missing or exceeds the small-body threshold. Unlike the small-body path, this subscriber
 * returns its {@link DataStream} <em>immediately</em> from {@link #getBody()} rather than
 * waiting for {@code onComplete}, so the caller can start reading bytes as they arrive on the
 * wire.
 *
 * <p>The subscriber is a thin adapter over {@link JavaHttpClientStreamingDataStream}, which
 * holds the buffer deque and coordinates the producer (this subscriber) with the consumer
 * (the {@link java.io.InputStream} returned by the caller). Callback responsibilities:
 * <ul>
 *   <li>{@code onSubscribe} — register the subscription with the DataStream so close() can
 *       cancel it, then request everything (JDK HttpClient paces delivery at the socket
 *       level, so unbounded demand is safe).</li>
 *   <li>{@code onNext(List)} — publish the whole batch in one lock acquisition on the
 *       DataStream, reducing per-buffer overhead compared to one call per {@code ByteBuffer}.</li>
 *   <li>{@code onError} / {@code onComplete} — terminate the DataStream so a parked reader
 *       wakes and observes end-of-stream (with an exception or cleanly).</li>
 * </ul>
 */
final class JavaHttpClientResponseBodySubscriber implements HttpResponse.BodySubscriber<DataStream> {
    private final JavaHttpClientStreamingDataStream stream;
    private final CompletionStage<DataStream> body;

    JavaHttpClientResponseBodySubscriber(HttpHeaders headers) {
        this.stream = new JavaHttpClientStreamingDataStream(
                headers.firstValue("content-type").orElse(null),
                headers.firstValueAsLong("content-length").orElse(-1L));
        // The body is handed to the caller as soon as headers are available; bytes stream
        // asynchronously into `stream` afterwards.
        this.body = CompletableFuture.completedFuture(stream);
    }

    @Override
    public CompletionStage<DataStream> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        stream.setSubscription(subscription);
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        stream.enqueueBatch(item);
    }

    @Override
    public void onError(Throwable throwable) {
        stream.fail(throwable);
    }

    @Override
    public void onComplete() {
        stream.complete();
    }
}
