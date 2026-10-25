/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

/**
 * Receives an array after its pooled stream releases ownership.
 *
 * <p>Implementations must be thread-safe and must not throw.
 */
@FunctionalInterface
public interface ByteArrayRecycler {
    /**
     * Takes ownership of an array with no remaining readers.
     *
     * @param array array whose contents can be overwritten
     */
    void recycle(byte[] array);
}
