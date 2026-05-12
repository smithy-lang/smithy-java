/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;

/**
 * Handles {@link AwsConfigCredentialSource.StaticKeys}.
 */
public final class StaticKeysHandler implements AwsConfigCredentialSourceHandler<AwsCredentialsIdentity> {

    public StaticKeysHandler() {}

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("n"));

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public IdentityResult<
            AwsCredentialsIdentity> tryResolve(AwsConfigCredentialSource source, ResolutionContext context) {
        if (source instanceof AwsConfigCredentialSource.StaticKeys(String accessKeyId, String secretAccessKey, String accountId)) {
            return IdentityResult.of(AwsCredentialsIdentity.create(
                    accessKeyId,
                    secretAccessKey,
                    null, // sessionToken
                    null, // expirationTime
                    accountId));
        }

        return null;
    }
}
