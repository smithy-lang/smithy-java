/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

public enum WaiterState {
    /**
     * Indicates the waiter succeeded and must no longer continue waiting.
     */
    SUCCESS,

    /**
     * Indicates the waiter failed and must not continue waiting.
     */
    FAILURE,

    /**
     * Indicates that the waiter encountered an expected failure case and should retry if possible.
     */
    RETRY;
}
