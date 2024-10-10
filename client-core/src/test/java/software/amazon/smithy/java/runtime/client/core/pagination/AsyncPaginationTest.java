/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.client.core.pagination.models.GetFoosInput;
import software.amazon.smithy.java.runtime.client.core.pagination.models.GetFoosOutput;
import software.amazon.smithy.java.runtime.client.core.pagination.models.ResultWrapper;
import software.amazon.smithy.java.runtime.client.core.pagination.models.TestOperationPaginated;

public class AsyncPaginationTest {
    private static final List<GetFoosOutput> BASE_EXPECTED_RESULTS = List.of(
        new GetFoosOutput(new ResultWrapper("first", List.of("foo0", "foo1"))),
        new GetFoosOutput(new ResultWrapper("second", List.of("foo0", "foo1"))),
        new GetFoosOutput(new ResultWrapper("third", List.of("foo0", "foo1"))),
        new GetFoosOutput(new ResultWrapper("final", List.of("foo0", "foo1"))),
        new GetFoosOutput(new ResultWrapper(null, List.of("foo0", "foo1")))
    );

    private MockClient mockClient;

    @BeforeEach
    public void setup() {
        mockClient = new MockClient();
    }

    @Test
    void testAsyncPagination() {
        var input = GetFoosInput.builder().maxResults(2).build();
        var paginator = AsyncPaginator.paginate(input, new TestOperationPaginated(), mockClient::getFoosAsync);
        var subscriber = new PaginationTestSubscriber();
        paginator.subscribe(subscriber);
        // Block and wait on results
        var results = subscriber.results();
        System.out.println("Results " + results);
        assertThat(results, contains(BASE_EXPECTED_RESULTS.toArray()));
    }

    @Test
    void testMaxItemsPagination() {
        var input = GetFoosInput.builder().maxResults(4).build();
        var paginator = AsyncPaginator.paginate(input, new TestOperationPaginated(), mockClient::getFoosAsync);
        paginator.maxItems(10);
        var subscriber = new PaginationTestSubscriber();
        paginator.subscribe(subscriber);

        // Block and wait on results
        var results = subscriber.results();
        System.out.println("RESULTS " + results);
        var expectedResult = List.of(
            new GetFoosOutput(new ResultWrapper("first", List.of("foo0", "foo1", "foo2", "foo3"))),
            new GetFoosOutput(new ResultWrapper("second", List.of("foo0", "foo1", "foo2", "foo3"))),
            new GetFoosOutput(new ResultWrapper("third", List.of("foo0", "foo1")))
        );
        assertThat(results, contains(expectedResult.toArray()));
    }

    private static final class PaginationTestSubscriber implements Flow.Subscriber<GetFoosOutput> {
        private Flow.Subscription subscription;
        private final BlockingQueue<GetFoosOutput> results = new LinkedBlockingQueue<>();
        private final CompletableFuture<List<GetFoosOutput>> future = new CompletableFuture<>();

        private List<GetFoosOutput> results() {
            return future.join();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            // Request a result
            subscription.request(1);
        }

        @Override
        public void onNext(GetFoosOutput item) {
            results.add(item);
            // request another
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            // Do nothing
        }

        @Override
        public void onComplete() {
            future.complete(results.stream().toList());
        }
    }
}
