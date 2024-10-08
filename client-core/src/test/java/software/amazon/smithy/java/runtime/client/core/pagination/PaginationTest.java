/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.client.core.pagination.models.GetFoosInput;
import software.amazon.smithy.java.runtime.client.core.pagination.models.GetFoosOutput;
import software.amazon.smithy.java.runtime.client.core.pagination.models.ResultWrapper;
import software.amazon.smithy.java.runtime.client.core.pagination.models.TestOperationPaginated;

public class PaginationTest {
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
    void testSyncPagination() {
        var input = GetFoosInput.builder().maxResults(2).build();
        var paginator = Paginator.paginate(input, new TestOperationPaginated(), mockClient::getFoosSync);
        List<GetFoosOutput> results = new ArrayList<>();
        for (var output : paginator) {
            results.add(output);
        }
        assertThat(results, contains(BASE_EXPECTED_RESULTS.toArray()));
    }

    @Test
    void testIteratorPagination() {
        var input = GetFoosInput.builder().maxResults(2).build();
        var paginator = Paginator.paginate(input, new TestOperationPaginated(), mockClient::getFoosSync);
        List<GetFoosOutput> results = new ArrayList<>();
        var iterator = paginator.iterator();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        assertThat(results, contains(BASE_EXPECTED_RESULTS.toArray()));
    }

    @Test
    public void testMaxResultsPagination() {
        var input = GetFoosInput.builder().maxResults(4).build();
        var paginator = Paginator.paginate(input, new TestOperationPaginated(), mockClient::getFoosSync);
        paginator.maxItems(10);

        List<GetFoosOutput> results = new ArrayList<>();
        for (var output : paginator) {
            results.add(output);
        }
        var expectedResult = List.of(
            new GetFoosOutput(new ResultWrapper("first", List.of("foo0", "foo1", "foo2", "foo3"))),
            new GetFoosOutput(new ResultWrapper("second", List.of("foo0", "foo1", "foo2", "foo3"))),
            new GetFoosOutput(new ResultWrapper("third", List.of("foo0", "foo1")))
        );
        assertThat(results, contains(expectedResult.toArray()));
    }
}
