/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;

final class DefaultAsyncPaginator<I extends SerializableStruct, O extends SerializableStruct> implements
    AsyncPaginator<O> {

    private final PaginatableAsync<I, O> call;
    private final PaginationInputFactory<I> inputFactory;
    private final PaginationTokenExtractor extractor;

    // Pagination parameters
    private volatile String nextToken = null;
    private int pageSize;
    private int totalMaxItems = 0;

    // Request override for paginated requests
    private RequestOverrideConfig overrideConfig = null;

    DefaultAsyncPaginator(I input, ApiOperation<I, O> operation, PaginatableAsync<I, O> call) {
        this.call = call;
        var trait = operation.schema().expectTrait(TraitKey.PAGINATED_TRAIT);
        // Input and output token paths are expected to be set.
        var inputTokenMember = trait.getInputToken().orElseThrow();
        var outputTokenPath = trait.getOutputToken().orElseThrow();

        // Page size and Items are optional
        var pageSizeMember = trait.getPageSize().orElse(null);
        var itemsPath = trait.getItems().orElse(null);

        this.inputFactory = new PaginationInputFactory<>(
            input,
            operation,
            inputTokenMember,
            pageSizeMember
        );

        if (pageSizeMember != null) {
            var pageSizeSchema = input.schema().member(pageSizeMember);
            pageSize = input.getMemberValue(pageSizeSchema);
        }

        this.extractor = new PaginationTokenExtractor(
            operation.outputSchema(),
            outputTokenPath,
            itemsPath
        );
    }

    @Override
    public void maxItems(int maxItems) {
        this.totalMaxItems = maxItems;
    }

    @Override
    public void overrideConfig(RequestOverrideConfig overrideConfig) {
        this.overrideConfig = overrideConfig;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super O> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {

            private final AtomicInteger remainingItems = new AtomicInteger(totalMaxItems);
            private final AtomicLong pendingRequests = new AtomicLong(0);
            private final AtomicInteger pendingExecutions = new AtomicInteger();
            private final AtomicBoolean completed = new AtomicBoolean(false);
            private int maxItems = pageSize;

            @Override
            public void request(long n) {
                if (n <= 0) {
                    subscriber.onError(new IllegalArgumentException("Requested items must be greater than 0"));
                }
                accumulate(pendingRequests, n);
                execute();
            }

            @Override
            public void cancel() {
                // Do nothing
            }

            private void execute() {
                // Only allow one pending execution at a time.
                if (pendingExecutions.getAndIncrement() > 0) {
                    // Cancel this execution
                    pendingExecutions.decrementAndGet();
                    return;
                }

                if (completed.get()) {
                    return;
                }

                try {
                    // If there are fewer items remaining than we would request, reduce page size to match remaining.
                    var remaining = remainingItems.get();
                    if (remaining > 0 && maxItems > remaining) {
                        maxItems = remaining;
                    }

                    // Get the updated input call with new values.
                    var input = inputFactory.create(nextToken, maxItems);

                    // The call and callback processing the output could be executed on a separate thread.
                    call.call(input, overrideConfig).thenAccept(output -> {
                        var res = extractor.extract(output);

                        // If we see the same pagination token twice then stop pagination.
                        if (nextToken != null && Objects.equals(nextToken, res.token())) {
                            completed.set(true);
                            subscriber.onComplete();
                        }
                        // Update token value for next call
                        nextToken = res.token();

                        // Update remaining items to get based on output values
                        var newRemaining = remainingItems.addAndGet(-res.totalItems());

                        // Send output to subscriber
                        subscriber.onNext(output);

                        // Next token is null or max results reached, indicating there are no more values.
                        if (nextToken == null || (totalMaxItems != 0 && newRemaining == 0)) {
                            completed.set(true);
                            subscriber.onComplete();

                            // There is no need to update pending requests or pending executions. Just exit.
                            return;
                        }

                        // Check if there are remaining requests or executions
                        var remainingRequests = pendingRequests.decrementAndGet();
                        // Mark the current execution as complete
                        pendingExecutions.decrementAndGet();

                        // If there are still remaining requests start another execution
                        if (remainingRequests > 0) {
                            execute();
                        }
                    });
                } catch (Exception exc) {
                    subscriber.onError(exc);
                }
            }
        });
    }

    private static void accumulate(AtomicLong l, long n) {
        l.accumulateAndGet(n, DefaultAsyncPaginator::accumulate);
    }

    private static long accumulate(long current, long n) {
        if (current == Long.MAX_VALUE || n == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        try {
            return Math.addExact(current, n);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public CompletableFuture<Void> forEach(Predicate<O> consumer) {
        var future = new CompletableFuture<Void>();
        subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(O item) {
                try {
                    if (consumer.test(item)) {
                        subscription.request(1);
                    } else {
                        subscription.cancel();
                        future.complete(null);
                    }
                } catch (RuntimeException exc) {
                    // Handle the consumer throwing an exception
                    subscription.cancel();
                    future.completeExceptionally(exc);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }
        });
        return future;
    }
}
