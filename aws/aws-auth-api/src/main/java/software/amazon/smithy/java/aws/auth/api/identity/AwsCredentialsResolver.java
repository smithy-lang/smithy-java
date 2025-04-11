/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.auth.api.identity;

import software.amazon.smithy.java.auth.api.identity.IdentityResolver;

/**
 * An {@link IdentityResolver} that resolves a {@link AwsCredentialsIdentity} for authentication.
 */
public interface AwsCredentialsResolver extends IdentityResolver<AwsCredentialsIdentity> {
    @Override
    default Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }
}
