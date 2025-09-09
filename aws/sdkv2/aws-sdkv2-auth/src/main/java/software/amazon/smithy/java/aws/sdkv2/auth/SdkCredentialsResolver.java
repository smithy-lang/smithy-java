/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.auth;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.context.Context;

public class SdkCredentialsResolver implements AwsCredentialsResolver {

    /**
     * The underlying SDK v2 credentials provider that will be used to resolve AWS credentials.
     */
    private final AwsCredentialsProvider sdkCredentialsProvider;

    /**
     * Creates a new resolver that uses the specified SDK credentials provider.
     *
     * @param sdkCredentialsProvider The SDK v2 credentials provider to use for resolving credentials.
     *                               Must not be null.
     */
    public SdkCredentialsResolver(AwsCredentialsProvider sdkCredentialsProvider) {
        this.sdkCredentialsProvider = sdkCredentialsProvider;
    }

    /**
     * Resolves AWS credentials from the underlying SDK credentials provider and converts them
     * to a Smithy {@link AwsCredentialsIdentity}.
     *
     * <p>If the resolved credentials are {@link AwsSessionCredentials}, the session token and
     * expiration time (if available) will be included in the identity.
     *
     * @param requestProperties The request properties used to resolve an Identity.
     * @return the resolved AWS credentials identity.
     */
    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        var credentials = sdkCredentialsProvider.resolveCredentials();
        AwsCredentialsIdentity identity;
        if (credentials instanceof AwsSessionCredentials s) {
            identity = AwsCredentialsIdentity
                    .create(s.accessKeyId(), s.secretAccessKey(), s.sessionToken(), s.expirationTime().orElse(null));
        } else {
            identity = AwsCredentialsIdentity.create(credentials.accessKeyId(), credentials.secretAccessKey());
        }
        return IdentityResult.of(identity);
    }
}
