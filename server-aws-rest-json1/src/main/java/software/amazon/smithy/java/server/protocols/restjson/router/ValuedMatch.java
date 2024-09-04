/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

/**
 * Extends the {@link Match} interface to add a value that maps to the
 * matching pattern.
 *
 * @param <T> The type that the URI patterns match to.
 */
public interface ValuedMatch<T> extends Match {
    /**
     * Returns the value that the matched pattern maps to.
     *
     * @return The value that the matched pattern maps to.
     */
    T getValue();

}
