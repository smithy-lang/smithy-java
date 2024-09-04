/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

/**
 * Builder interface for the UriMatcherMap class.
 *
 * @param <T> The type that the URI patterns map to.
 */
public interface UriMatcherMapBuilder<T> {
    /**
     * Add a new pattern and mapped value to the builder.
     *
     * @param pattern The URI pattern
     * @param value   The value that the URI pattern matches map to.
     */
    void add(UriPattern pattern, T value);

    /**
     * Builds the new UriMatcherMap class.
     *
     * @return The newly built {@code UriMatcherMap} instance.
     */
    UriMatcherMap<T> build();
}
