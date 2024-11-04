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
import java.util.function.Predicate;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

final class DefaultAsyncPaginator<I extends SerializableStruct, O extends SerializableStruct> implements
    AsyncPaginator<O> {

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
            private final AtomicBoolean completed = new AtomicBoolean(false);
            private final AtomicInteger remainingItems = new AtomicInteger(totalMaxItems);
            private final AtomicLong remainingRequests = new AtomicLong(0);
            private int maxItems = pageSize;

            @Override
            public void request(long n) {
                if (n <= 0) {
                    subscriber.onError(new IllegalArgumentException("Requested items must be greater than 0"));
                }
                remainingRequests.addAndGet(n);
                execute();
            }

            @Override
            public void cancel() {
                // Do nothing
            }

            private void execute() {
                if (completed.get()) {
                    return;
                }
                remainingRequests.decrementAndGet();

                // If there are fewer items remaining than we would request, reduce page size to match remaining.
                if (remainingItems.get() > 0 && maxItems > remainingItems.get()) {
                    maxItems = remainingItems.get();
                }

                try {
                    // Deserialize a new version of the original input with the new token and max value set.
                    var deserializer = new PaginationInjectingDeserializer(
                        inputDocument,
                        inputTokenMember,
                        nextToken,
                        pageSizeMember,
                        maxItems
                    );
                    var input = operation.inputBuilder().deserialize(deserializer).build();
                    call.call(input, overrideConfig).thenAccept(output -> {
                        // Use a serializer to extract relevant data from the response
                        var serializer = new PaginationDiscoveringSerializer(outputTokenPath, itemsPath);
                        output.serialize(serializer);
                        serializer.flush();

                        // If we see the same pagination token twice then stop pagination.
                        if (nextToken != null && Objects.equals(nextToken, serializer.outputToken())) {
                            completed.set(true);
                            subscriber.onComplete();
                        }

                        // Update based on output values
                        nextToken = serializer.outputToken();
                        remainingItems.getAndAdd(-serializer.totalItems());

                        // Next token is null or max results reached, indicating there are no more values.
                        if (nextToken == null || (totalMaxItems != 0 && remainingItems.get() == 0)) {
                            completed.set(true);
                            subscriber.onNext(output);
                            subscriber.onComplete();
                        } else {
                            subscriber.onNext(output);
                        }

                        // Make any remaining requests if necessary
                        if (remainingRequests.get() > 0) {
                            execute();
                        }
                    });
                } catch (Exception exc) {
                    subscriber.onError(exc);
                }
            }
        });
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
