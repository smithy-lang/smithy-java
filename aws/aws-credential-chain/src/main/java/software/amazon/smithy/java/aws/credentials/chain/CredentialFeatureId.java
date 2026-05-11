/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import software.amazon.smithy.java.client.core.FeatureId;

/**
 * A {@link FeatureId} for credential resolution business metrics.
 *
 * @param id the short feature ID string (e.g., "n" for profile static credentials).
 */
public record CredentialFeatureId(String id) implements FeatureId {
    @Override
    public String getShortName() {
        return id;
    }
}
