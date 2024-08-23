/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class SignerCacheTest {
    @Test
    void cacheEvictsOldestEntryAtMax() {
        var cache = new SigningCache(2);
        var value = new SigningKey("".getBytes(), Instant.EPOCH);
        cache.put("first", value);
        cache.put("second", value);
        assertThat(cache, hasEntry("first", value));
        assertThat(cache, hasEntry("second", value));

        // This should exceed cache limit and evict "first"
        cache.put("third", value);
        assertThat(cache, hasEntry("third", value));
        assertThat(cache, hasEntry("second", value));
        assertThat(cache, not(hasEntry("first", value)));
    }
}
