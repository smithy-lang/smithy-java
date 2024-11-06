/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

final class DefaultAsyncPaginator<I extends SerializableStruct, O extends SerializableStruct> implements
    AsyncPaginator<O> {
    private static final Complete COMPLETE = new Complete();

    private final Document inputDocument;
    private final PaginatableAsync<I, O> call;
    private final ApiOperation<I, O> operation;

    // Token members
    private final String inputTokenMember;
    private final String outputTokenPath;
    private volatile String nextToken = null;

    // Page size parameters
    private final String itemsPath;
    private final String pageSizeMember;
    private int pageSize;
    private int totalMaxItems = 0;

    // Request override for paginated requests
    private RequestOverrideConfig overrideConfig = null;

    DefaultAsyncPaginator(I input, ApiOperation<I, O> operation, PaginatableAsync<I, O> call) {
        this.inputDocument = Document.createTyped(input);
        this.call = call;
        this.operation = operation;
        var trait = operation.schema().expectTrait(TraitKey.PAGINATED_TRAIT);
        this.inputTokenMember = trait.getInputToken().orElseThrow();
        this.outputTokenPath = trait.getOutputToken().orElseThrow();
        this.itemsPath = trait.getItems().orElse(null);
        this.pageSizeMember = trait.getPageSize().orElse(null);
        if (pageSizeMember != null) {
            pageSize = inputDocument.getMember(pageSizeMember).asNumber().intValue();
        }
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

            private final AtomicReference<Throwable> terminalEvent = new AtomicReference<>();
            private final AtomicInteger remainingItems = new AtomicInteger(totalMaxItems);
            private final AtomicLong pendingRequests = new AtomicLong(0);
            private final AtomicInteger pendingExecutions = new AtomicInteger();

            private boolean completed;
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
                    return;
                }

                if (completed) {
                    return;
                }

                try {
                    // If there are fewer items remaining than we would request, reduce page size to match remaining.
                    var remaining = remainingItems.get();
                    if (remaining > 0 && maxItems > remaining) {
                        maxItems = remaining;
                    }

                    // Get the updated input call with new values.
                    var input = getInput(maxItems);


                } catch (Exception exc) {
                    subscriber.onError(exc);
                }
            }
        });

        private void callNextPage(Flow.Subscriber<> subscriber, I input) {
            call.call(input, overrideConfig).thenAccept(output -> {

                // Use a serializer to extract relevant data from the response
                var serializer = new PaginationDiscoveringSerializer(outputTokenPath, itemsPath);
                output.serialize(serializer);
                serializer.flush();

                // Get the next token to use
                var newToken = serializer.outputToken();
                // If we see the same pagination token twice then stop pagination.
                if (nextToken != null && Objects.equals(nextToken, newToken)) {
                    completed = true;
                    subscriber.onComplete();
                }
                nextToken = newToken;

                // Update remianing items to get based on output values
                remainingItems.getAndAdd(-serializer.totalItems());

                // Next token is null or max results reached, indicating there are no more values.
                if (nextToken == null || (totalMaxItems != 0 && remainingItems.get() == 0)) {
                    completed = true;
                    subscriber.onNext(output);
                    subscriber.onComplete();
                } else {
                    subscriber.onNext(output);
                }
            });
    }

    /**
     * Deserialize a new version of the original input with the new token and max value set.
     * @param maxItems
     * @return
     */
    private I getInput(int maxItems) {
        var deserializer = new PaginationInjectingDeserializer(
                inputDocument,
                inputTokenMember,
                nextToken,
                pageSizeMember,
                maxItems
        );
        return operation.inputBuilder().deserialize(deserializer).build();
    }

    /**
     * @return true if this decoder is in a terminal state
     */
    private boolean attemptTermination(Flow.Subscriber<? super O> subscriber, Throwable term, boolean done) {
        if (done && subscriber != null) {
            if (term == COMPLETE) {
                subscriber.onComplete();
            } else {
                subscriber.onError(term);
            }
            return true;
        }

        return false;
    }

    /**
     * Tries to flush up to the given demand and signals if we need data from
     * upstream if there is unfulfilled demand.
     *
     * @param outstanding outstanding message demand to fulfill
     * @return number of fulfilled requests
     */
    private long sendMessages(long outstanding) {
        long served = 0;
        while (served < outstanding) {
            ByteBuffer m = queue.poll();
            if (m == null) {
                break;
            }
            served++;
            subscriber.onNext(m);
        }

        return served;
    }

    private static long accumulate(AtomicLong l, long n) {
        return l.accumulateAndGet(n, DefaultAsyncPaginator::accumulate);
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

    private static final class Complete extends RuntimeException {
        Complete() {
            super("already complete", null, false, false);
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
