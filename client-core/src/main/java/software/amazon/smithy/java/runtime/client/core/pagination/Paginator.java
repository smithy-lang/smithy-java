/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Paginator that automates the retrieval of paginated results from a service.
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
 * <p>To consume the paginated data from this paginator you can either create an iterator for the paginated
 * data and manually iterate through the paginated results or use an enhanced for-loop to iterate over
 * results. For example, you could iterate through the paginate results of a {@code ListFoos} operation as follows:
 * <pre>{@code
 *  // Loop through paginated results
 *  for (ListFoosOutput result : paginator) {
 *      System.out.println(result);
 *  }
 * }</pre>
 *
 * <p><strong>Note:</strong>This paginator expects fully-resolved paginated traits on any paginated operation schemas
 * and will not automatically merge operation pagination info with a service's pagination info.
 *
 * @param <O> Output type of list operation being paginated.
 */
public interface Paginator<O extends SerializableStruct> extends PaginatorSettings, Iterable<O> {

    /**
     * Interface representing a function that is synchronously paginatable.
     */
    @FunctionalInterface
    interface Paginatable<I extends SerializableStruct, O extends SerializableStruct> {
        O call(I input, RequestOverrideConfig requestContext);
    }

    /**
     * Create a new {@link Paginator} for a given operation and input.
     *
     * @param input Base input to use for repeated requests to service.
     * @param operation API model for operation being paginated.
     * @param call Synchronous call that retrieves pages from service.
     * @return Asynchronous paginator
     *
     * @param <I> Operation input shape type.
     * @param <O> Operation output shape type.
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Paginator<O> paginate(
        I input,
        ApiOperation<I, O> operation,
        Paginatable<I, O> call
    ) {
        return new DefaultSyncPaginator<>(input, operation, call);
    }
}
