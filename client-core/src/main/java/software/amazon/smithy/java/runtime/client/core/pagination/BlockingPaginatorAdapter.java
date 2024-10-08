/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Adapter to treat async Paginator as a synchronous paginator by blocking on each page.
 */
final class BlockingPaginatorAdapter<O extends SerializableStruct> implements Paginator<O> {

    AsyncPaginator<O> asyncPaginator;

    BlockingPaginatorAdapter(AsyncPaginator<O> asyncPaginator) {
        this.asyncPaginator = asyncPaginator;
    }

    @Override
    public Iterator<O> iterator() {
        return new Iter(asyncPaginator);
    }

    @Override
    public void maxItems(int maxItems) {
        asyncPaginator.maxItems(maxItems);
    }

    @Override
    public void overrideConfig(RequestOverrideConfig overrideConfig) {
        asyncPaginator.overrideConfig(overrideConfig);
    }

    private final class Iter implements Iterator<O>, Flow.Subscriber<O> {
        private Flow.Subscription subscription;
        private boolean complete;
        CompletableFuture<O> next = new CompletableFuture<>();

        private Iter(Flow.Publisher<O> publisher) {
            publisher.subscribe(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(O item) {
            next.complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            next.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            complete = true;
            subscription = null;
        }

        @Override
        public boolean hasNext() {
            return !complete;
        }

        @Override
        public O next() {
            // Create new future to block on getting
            next = new CompletableFuture<>();
            // Request a new value
            subscription.request(1);
            return next.join();
        }
    }
}
