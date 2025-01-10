/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

public class HttpClientDataStreamTest {

    private static List<List<ByteBuffer>> createCannedBuffers() {
        return List.of(
                List.of(ByteBuffer.wrap("{\"hi\":".getBytes(StandardCharsets.UTF_8))),
                List.of(
                        ByteBuffer.wrap("[1, ".getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.wrap("2]".getBytes(StandardCharsets.UTF_8))),
                List.of(ByteBuffer.wrap("}".getBytes(StandardCharsets.UTF_8))));
    }

    private static final class CannedPublisher extends SubmissionPublisher<List<ByteBuffer>> {
        void pushData(List<List<ByteBuffer>> data) {
            data.forEach(this::submit);
            close();
        }
    }

    @Test
    public void convertsToBb() throws Exception {
        var httpPublisher = new CannedPublisher();
        var ds = new HttpClientDataStream(httpPublisher, 13, "application/json");
        assertThat(ds.contentType(), equalTo("application/json"));
        assertThat(ds.contentLength(), equalTo(13L));

        var cf = ds.asByteBuffer();
        httpPublisher.pushData(createCannedBuffers());

        var bb = cf.get();
        assertThat(bb.remaining(), equalTo(13));
        assertThat(new String(bb.array(), StandardCharsets.UTF_8), equalTo("{\"hi\":[1, 2]}"));
    }

    @Test
    public void convertsToInputStream() throws Exception {
        var httpPublisher = new CannedPublisher();
        var ds = new HttpClientDataStream(httpPublisher, 13, "application/json");
        var cf = ds.asInputStream();
        httpPublisher.pushData(createCannedBuffers());

        var is = cf.get();
        assertThat(new String(is.readAllBytes(), StandardCharsets.UTF_8), equalTo("{\"hi\":[1, 2]}"));
    }

    @Test
    public void convertsToPublisher() throws Exception {
        var httpPublisher = new CannedPublisher();
        var ds = new HttpClientDataStream(httpPublisher, 13, "application/json");

        var collector = new CollectingSubscriber();
        ds.subscribe(collector);
        httpPublisher.pushData(createCannedBuffers());
        var results = collector.getResult().get();

        assertThat(results, equalTo("{\"hi\":[1, 2]}"));
    }

    public static final class CollectingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final List<String> buffers = Collections.synchronizedList(new ArrayList<>());
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            buffers.add(new String(item.array(), StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            StringBuilder builder = new StringBuilder();
            for (String buffer : buffers) {
                builder.append(buffer);
            }
            result.complete(builder.toString());
        }

        public CompletableFuture<String> getResult() {
            return result;
        }
    }
}
