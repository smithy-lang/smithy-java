/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

/**
 * Represents a set of mapping between a URI patterns to values of a
 * generic type T. The match method can be used to match a concrete
 * URI against all the patterns in this class and return a value that
 * contains the mapped value and the captured labels.
 *
 * @param <T> The type that the URI patterns map to.
 */
public interface UriMatcherMap<T> {
    /**
     * Matches a URI and returns a {@link ValuedMatch} that contains
     * the value mapped with also the labels captured.
     *
     * @param uri The URI to match against.
     * @return The ValuedMatch instance that contains the captured
     * labels and the mapped value.
     */
    ValuedMatch<T> match(String uri);
}
