/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * Handles {@link AwsConfigCredentialSource.StaticKeys}.
 */
public final class StaticKeysHandler implements AwsConfigCredentialSourceHandler {

    public StaticKeysHandler() {}

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
