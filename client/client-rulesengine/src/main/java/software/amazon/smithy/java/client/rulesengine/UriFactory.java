/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache for URI parsing.
 *
 * <p>Maintains two hot slots for the most recently accessed URIs to avoid hash computation and LinkedHashMap
 * traversal for common access patterns. Falls back to the full LRU cache for less frequent URIs.
 */
final class UriFactory extends LinkedHashMap<String, URI> {

    private static final int DEFAULT_MAX_SIZE = 32;
    private final int maxSize;

    private String hotKey1;
    private URI hotValue1;
    private String hotKey2;
    private URI hotValue2;

    UriFactory() {
        this(DEFAULT_MAX_SIZE);
    }

    UriFactory(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, URI> eldest) {
        return size() > maxSize;
    }

    URI createUri(String uri) {
        if (uri == null) {
            return null;
        }

        if (uri.equals(hotKey1)) {
            return hotValue1;
        }

        if (uri.equals(hotKey2)) {
            // Promote to hot slot 1
            String tempKey = hotKey1;
            URI tempValue = hotValue1;
            hotKey1 = hotKey2;
            hotValue1 = hotValue2;
            hotKey2 = tempKey;
            hotValue2 = tempValue;
            return hotValue1;
        }

        // Fall back to full LRU cache
        URI result = get(uri);
        if (result == null) {
            try {
                result = URI.create(uri);
                put(uri, result);
            } catch (IllegalArgumentException ignored) {
                // Don't cache invalid URIs in hot slots
                return null;
            }
        }

        // Update hot-key cache: shift and insert
        hotKey2 = hotKey1;
        hotValue2 = hotValue1;
        hotKey1 = uri;
        hotValue1 = result;

        return result;
    }
}
