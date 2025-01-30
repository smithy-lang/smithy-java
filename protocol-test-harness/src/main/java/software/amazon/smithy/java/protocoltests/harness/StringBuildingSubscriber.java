/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

/**
 * Subscriber that extracts the message body as a string
 */
public final class StringBuildingSubscriber implements Flow.Subscriber<ByteBuffer> {
    private final StringBuilder builder = new StringBuilder();
    private final CompletableFuture<String> result = new CompletableFuture<>();

    public StringBuildingSubscriber(Flow.Publisher<ByteBuffer> flow) {
        flow.subscribe(this);
    }

    public String getResult() {
        try {
            return result.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Could not read flow as string", e);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(ByteBuffer item) {
        builder.append(StandardCharsets.UTF_8.decode(item));
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        result.complete(builder.toString());
    }
}
