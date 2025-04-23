/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.smithy.java.server.TestStructs.MockApiOperation;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link OperationFilters} default filters.
 */
public class OperationFiltersTest {

    /**
     * Creates a mock operation with the given name.
     */
    @Test
    public void testAllowListFilter() {
        // Create an allowlist filter with specific operation names
        var filter = OperationFilters.allowList(Set.of("GetItem", "PutItem"));

        // Operations in the allowlist should be included
        assertThat(filter.test(new MockApiOperation("GetItem"))).isTrue();
        assertThat(filter.test(new MockApiOperation("PutItem"))).isTrue();

        // Operations not in the allowlist should be excluded
        assertThat(filter.test(new MockApiOperation("DeleteItem"))).isFalse();
        assertThat(filter.test(new MockApiOperation("ListItems"))).isFalse();
    }

    @Test
    public void testAllowListFilterWithEmptySet() {
        // An empty allowlist should include all operations
        var filter = OperationFilters.allowList(Set.of());

        assertThat(filter.test(new MockApiOperation("GetItem"))).isTrue();
        assertThat(filter.test(new MockApiOperation("PutItem"))).isTrue();
        assertThat(filter.test(new MockApiOperation("DeleteItem"))).isTrue();
    }

    @Test
    public void testBlockListFilter() {
        // Create a blocklist filter with specific operation names
        var filter = OperationFilters.blockList(Set.of("DeleteItem", "UpdateItem"));

        // Operations in the blocklist should be excluded
        assertThat(filter.test(new MockApiOperation("DeleteItem"))).isFalse();
        assertThat(filter.test(new MockApiOperation("UpdateItem"))).isFalse();

        // Operations not in the blocklist should be included
        assertThat(filter.test(new MockApiOperation("GetItem"))).isTrue();
        assertThat(filter.test(new MockApiOperation("PutItem"))).isTrue();
    }
}
