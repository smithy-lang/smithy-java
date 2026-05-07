/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * Handles {@link AwsConfigCredentialSource.SessionKeys}.
 */
public final class SessionKeysHandler implements AwsConfigCredentialSourceHandler {

    public SessionKeysHandler() {}

    @Override
    public IdentityResult<
            AwsCredentialsIdentity> tryResolve(AwsConfigCredentialSource source, ResolutionContext context) {
        if (source instanceof AwsConfigCredentialSource.SessionKeys(String accessKeyId, String secretAccessKey, String sessionToken, String accountId)) {
            return IdentityResult.of(AwsCredentialsIdentity.create(
                    accessKeyId,
                    secretAccessKey,
                    sessionToken,
                    null, // expirationTime
                    accountId));
        }

        return null;
    }
}
