/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

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
 * the service. The following example shows how to implement a subscriber that processes paginated results from
 * a {@code ListFoos} operation.
 *
 * <pre>{@code
*  // Use a subscriber to read results from the paginator
*  paginator.subscribe(new Subscriber<ListFoosResult>() {
*       private Subscription subscription;
*
*       @Override
*       public void onSubscribe(Subscription s) {
 *          // store subscription and make initial request for a single
 *          // page of results.
*           subscription = s;
*           subscription.request(1);
*       }
*
*       @Override
*       public void onNext(ListFoosResult result) {
 *          // Process returned result and request a new page.
*           System.out.println(result);
*           subscription.request(1);
*       }
*
*       @Override
*       public void onError(Throwable t) {}
*
*       @Override
*       public void onComplete() {}
*  });
 * }</pre>
 *
 * @param <O> Output type of list operation being paginated.
 */
public interface AsyncPaginator<O extends SerializableStruct> extends PaginatorSettings, Flow.Publisher<O> {

    /**
     * Converts this {@code AsyncPaginator} to a blocking {@link Paginator}.
     *
     * @return blocking paginator.
     */
    default Paginator<O> toBlocking() {
        // TODO: support conversion to blocking paginator
        throw new UnsupportedOperationException("Blocking pagination is not supported yet.");
    }

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
     * @param consumer  Consumer to process event.
     * @return CompletableFuture that will be notified when all events have been consumed or if an error occurs.
     */
    CompletableFuture<Void> forEach(Consumer<O> consumer);
}
