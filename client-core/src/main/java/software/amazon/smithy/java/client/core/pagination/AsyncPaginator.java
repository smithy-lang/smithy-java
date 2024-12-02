/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Predicate;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Asynchronous paginator that automates the retrieval of paginated results from a service.
 *
 * <p>Many list operations return paginated results when the response object is too large
 * to return in a single response. Paginators can help you navigate through paginated responses
 * from services. Typically, a service will return a truncated response when the response length
 * is greater than a certain limit. This response will also contain a next token to use in a
 * subsequent request. In order to consume all the data, you could create a loop that makes
 * multiple requests, replacing the next token in each iteration. However, with paginators this
 * extra loop isn’t necessary because the Paginator handles all of that logic for you;
 * in other words, you can retrieve all the data with less code.
 *
 * <p>To consume the paginated data from this paginator, implement a {@link Flow.Subscriber} and subscribe it
 * to this flow. Each time your subscriber requests a new result, this paginator will request a new page of results from
 * the service. The following example shows how to process each page of results from a paginator:
 *
 * <pre>{@code
 *  // Page through all results till we find some specific item.
 *  paginator.forEach(result -> {
 *      if (result.items().stream().anyMatch(item -> "targetId".equals(item.id())) {
 *          // Do something with the result.
 *          logSpecialItem(result);
 *          // Stop paginating as we have found the item we are looking for
 *          return false;
 *      } else {
 *          // Otherwise keep paging through results
 *          return true;
 *      }
 *  })
 * }</pre>
 *
 * <p><strong>Note:</strong>This paginator expects fully-resolved paginated traits on any paginated operation schemas
 * and will not automatically merge operation pagination info with a service's pagination info.
 *
 * @param <O> Output type of list operation being paginated.
 */
public interface AsyncPaginator<O extends SerializableStruct> extends PaginatorSettings, Flow.Publisher<O> {

    /**
     * Interface representing a function that is asynchronously paginatable.
     */
    @FunctionalInterface
    interface PaginatableAsync<I extends SerializableStruct, O extends SerializableStruct> {
        CompletableFuture<O> call(I input, RequestOverrideConfig requestContext);
    }

    /**
     * Create a new {@link AsyncPaginator} for a given operation and input.
     *
     * @param input Base input to use for repeated requests to service.
     * @param operation API model for operation being paginated.
     * @param call Asynchronous call that retrieves pages from service.
     * @return Asynchronous paginator
     * @param <I> Operation input shape type.
     * @param <O> Operation output shape type.
     */
    static <I extends SerializableStruct, O extends SerializableStruct> AsyncPaginator<O> paginate(
        I input,
        ApiOperation<I, O> operation,
        PaginatableAsync<I, O> call
    ) {
        return new DefaultAsyncPaginator<>(input, operation, call);
    }

    /**
     * Subscribes to the publisher with the given Consumer.
     *
     * <p>This consumer will be called for each event published (without backpressure). If more control or
     * backpressure is required, consider using {@link Flow.Publisher#subscribe(Flow.Subscriber)}.
     *
     * @param predicate Consumer to process pages of results. Return true from predicate to keep processing next page, false to stop.
     * @return CompletableFuture that will be notified when all events have been consumed or if an error occurs.
     */
    default CompletableFuture<Void> forEach(Predicate<O> predicate) {
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
                    if (predicate.test(item)) {
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
