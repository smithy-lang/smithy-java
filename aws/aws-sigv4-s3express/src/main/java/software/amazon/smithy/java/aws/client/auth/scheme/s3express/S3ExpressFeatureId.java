/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.client.core.FeatureId;

/**
 * Business-metric IDs published when this module's auth scheme runs successfully.
 */
enum S3ExpressFeatureId implements FeatureId {
    /**
     * Emitted on every operation call that resolves a bucket-scoped S3 Express identity.
     */
    S3_EXPRESS_BUCKET("J");

    private final String shortName;

    S3ExpressFeatureId(String shortName) {
        this.shortName = shortName;
    }

    @Override
    public String getShortName() {
        return shortName;
    }
}
