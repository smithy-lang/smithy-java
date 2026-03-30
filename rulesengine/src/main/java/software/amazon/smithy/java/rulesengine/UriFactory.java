/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * LRU cache for URI parsing.
 *
 * <p>Maintains a hot slot for the most recently accessed URI to avoid hash computation and LinkedHashMap traversal.
 */
final class UriFactory extends LinkedHashMap<String, SmithyUri> {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_MAX_SIZE = 32;
    private final int maxSize;

    private String hotKey;
    private transient SmithyUri hotValue;

    UriFactory() {
        this(DEFAULT_MAX_SIZE);
    }

    UriFactory(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, SmithyUri> eldest) {
        return size() > maxSize;
    }

    SmithyUri createUri(String uri) {
        if (uri == null) {
            return null;
        }

        if (uri.equals(hotKey)) {
            return hotValue;
        }

        // Fall back to full LRU cache
        SmithyUri result = get(uri);
        if (result == null) {
            try {
                result = SmithyUri.of(uri);
                put(uri, result);
            } catch (IllegalArgumentException ignored) {
                // Don't cache invalid URIs in hot slot
                return null;
            }
        }

        // Update hot-key cache
        hotKey = uri;
        hotValue = result;

        return result;
    }
}
