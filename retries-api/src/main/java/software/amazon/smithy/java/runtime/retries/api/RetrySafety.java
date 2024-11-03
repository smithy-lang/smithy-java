/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

/**
 * Whether it's safe to retry.
 */
public enum RetrySafety {
    /**
     * Yes, it is safe to retry this error.
     */
    YES,

    /**
     * No, a retry should not be made because it isn't safe to retry.
     */
    NO,

    /**
     * Not enough information is available to determine if a retry is safe.
     */
    MAYBE
}
