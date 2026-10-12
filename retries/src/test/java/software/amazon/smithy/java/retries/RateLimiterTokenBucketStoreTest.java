/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RateLimiterTokenBucketStoreTest {

    @Test
    void returnsSameBucketForSameScope() {
        var store = new RateLimiterTokenBucketStore(2);

        var first = store.tokenBucketForScope("a");

        assertThat(store.tokenBucketForScope("a")).isSameAs(first);
    }

    @Test
    void returnsDistinctBucketsForDistinctScopes() {
        var store = new RateLimiterTokenBucketStore(2);

        assertThat(store.tokenBucketForScope("a")).isNotSameAs(store.tokenBucketForScope("b"));
    }

    @Test
    void evictsLeastRecentlyUsedScopeBeyondMaxScopes() {
        var store = new RateLimiterTokenBucketStore(2);
        var a = store.tokenBucketForScope("a");
        var b = store.tokenBucketForScope("b");

        var c = store.tokenBucketForScope("c");

        assertThat(store.tokenBucketForScope("b")).isSameAs(b);
        assertThat(store.tokenBucketForScope("c")).isSameAs(c);
        assertThat(store.tokenBucketForScope("a")).isNotSameAs(a);
    }

    @Test
    void lookupCountsAsAccessForEvictionOrder() {
        var store = new RateLimiterTokenBucketStore(2);
        var a = store.tokenBucketForScope("a");
        var b = store.tokenBucketForScope("b");

        assertThat(store.tokenBucketForScope("a")).isSameAs(a);
        store.tokenBucketForScope("c");

        assertThat(store.tokenBucketForScope("a")).isSameAs(a);
        assertThat(store.tokenBucketForScope("b")).isNotSameAs(b);
    }

    @Test
    void rejectsNullScope() {
        var store = new RateLimiterTokenBucketStore(2);

        assertThatThrownBy(() -> store.tokenBucketForScope(null)).isInstanceOf(NullPointerException.class);
    }
}
