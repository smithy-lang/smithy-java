/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

/**
 * Indicate that something is a feature ID used with {@link CallContext#FEATURE_IDS}.
 */
public interface FeatureId {
    /**
     * Gets the short name of the feature (e.g., "P1").
     *
     * <p>Calls {@link Object#toString()} by default.
     *
     * @return the short name of the feature.
     */
    default String getShortName() {
        return toString();
    }
}
