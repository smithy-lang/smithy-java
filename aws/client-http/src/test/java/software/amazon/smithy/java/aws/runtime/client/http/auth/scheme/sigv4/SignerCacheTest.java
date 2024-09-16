/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class SignerCacheTest {
    @Test
    void cacheEvictsOldestEntryAtMax() {
        var cache = new SigningCache(2);
        var value = new SigningKey("".getBytes(), Instant.EPOCH);
        cache.put("first", value);
        cache.put("second", value);
        assertEquals(cache.get("first"), value);
        assertEquals(cache.get("second"), value);

        // This should exceed cache limit and evict "first"
        cache.put("third", value);

        assertEquals(cache.get("third"), value);
        assertEquals(cache.get("second"), value);
        assertNull(cache.get("first"));
    }
}
