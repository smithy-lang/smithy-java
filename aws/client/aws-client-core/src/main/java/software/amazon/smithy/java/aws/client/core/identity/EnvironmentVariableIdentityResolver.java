/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.context.Context;

/**
 * {@link AwsCredentialsResolver} implementation that loads credentials from environment variables.
 *
 * <p>This resolver reads environment variables once on first access and caches the result. Use
 * {@link #invalidate()} to force re-reading (e.g., in tests).
 *
 * <p>Expected environment variables:
 * <dl>
 *     <dt>{@code AWS_ACCESS_KEY_ID}</dt>
 *     <dd>Sets the AWS Access Key for the identity</dd>
 *     <dt>{@code AWS_SECRET_ACCESS_KEY}</dt>
 *     <dd>Sets the AWS Secret Key for the identity</dd>
 *     <dt>{@code AWS_SESSION_TOKEN}</dt>
 *     <dd>(optional) Security token provided by the AWS Security Token Service (STS) for temporary credentials</dd>
 *     <dt>{@code AWS_ACCOUNT_ID}</dt>
 *     <dd>(optional) AWS account ID</dd>
 * </dl>
 */
public final class EnvironmentVariableIdentityResolver implements AwsCredentialsResolver {
    public static final EnvironmentVariableIdentityResolver INSTANCE = new EnvironmentVariableIdentityResolver();

    private static final String ACCESS_KEY_PROPERTY = "AWS_ACCESS_KEY_ID";
    private static final String SECRET_KEY_PROPERTY = "AWS_SECRET_ACCESS_KEY";
    private static final String SESSION_TOKEN_PROPERTY = "AWS_SESSION_TOKEN";
    private static final String ACCOUNT_ID_PROPERTY = "AWS_ACCOUNT_ID";
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND = IdentityResult.ofError(
            EnvironmentVariableIdentityResolver.class,
            "Could not resolve an AWS identity using the AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment "
                    + "variables");

    private volatile IdentityResult<AwsCredentialsIdentity> cached;

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        IdentityResult<AwsCredentialsIdentity> result = cached;
        if (result != null) {
            return result;
        }

        result = resolve();
        cached = result;
        return result;
    }

    @Override
    public void invalidate() {
        cached = null;
    }

    private static IdentityResult<AwsCredentialsIdentity> resolve() {
        String accessKey = System.getenv(ACCESS_KEY_PROPERTY);
        String secretKey = System.getenv(SECRET_KEY_PROPERTY);
        if (accessKey == null || secretKey == null) {
            return NOT_FOUND;
        }

        String sessionToken = System.getenv(SESSION_TOKEN_PROPERTY);
        String accountId = System.getenv(ACCOUNT_ID_PROPERTY);
        return IdentityResult.of(AwsCredentialsIdentity.create(accessKey, secretKey, sessionToken, null, accountId));
    }
}
