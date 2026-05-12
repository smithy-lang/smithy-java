/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.auth.api.identity;

import software.amazon.smithy.java.auth.api.identity.IdentityResolver;

/**
 * An {@link IdentityResolver} that resolves a {@link AwsCredentialsIdentity} for authentication.
 *
 * <p>Note: this is a convenience only. Do not rely on this for limiting subtypes.
 */
public interface AwsCredentialsResolver extends IdentityResolver<AwsCredentialsIdentity> {
    @Override
    default Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }
}
