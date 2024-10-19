/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

/**
 * A container that provides members by name.
 */
public interface MemberLookup {
    /**
     * Get a member by name.
     *
     * @param memberName Member by name to get.
     * @return Returns the found member or null if not found.
     */
    Schema member(String memberName);
}
