/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

/**
 * Flags that can be used with {@link Document#equals(Object, Object, int)}.
 */
public final class DocumentEqualsFlags {

    private DocumentEqualsFlags() {}

    /**
     * Compares numbers using widening type promotion, like JLS 5.1.2.
     */
    public static final int NUMBER_PROMOTION = 1 << 0; // 0001
}
