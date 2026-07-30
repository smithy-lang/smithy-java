/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.context.Context;

/**
 * {@link AwsCredentialsResolver} implementation that loads credentials from environment variables.
 *
 * <p>This resolver reads its source once on first access and caches the result. Call
 * {@link #invalidate(AwsCredentialsIdentity)} with the cached identity to force re-reading (e.g., in tests).
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

    static final String ACCESS_KEY_PROPERTY = "AWS_ACCESS_KEY_ID";
    static final String SECRET_KEY_PROPERTY = "AWS_SECRET_ACCESS_KEY";
    static final String SESSION_TOKEN_PROPERTY = "AWS_SESSION_TOKEN";
    static final String ACCOUNT_ID_PROPERTY = "AWS_ACCOUNT_ID";
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND = IdentityResult.ofError(
            EnvironmentVariableIdentityResolver.class,
            "Could not resolve an AWS identity using the AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment "
                    + "variables");

    private final AtomicReference<IdentityResult<AwsCredentialsIdentity>> cached = new AtomicReference<>();

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        while (true) {
            IdentityResult<AwsCredentialsIdentity> result = cached.get();
            if (result != null) {
                return result;
            }

            result = resolve();
            if (cached.compareAndSet(null, result)) {
                return result;
            }
        }
    }

    @Override
    public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
        IdentityResult<AwsCredentialsIdentity> current = cached.get();
        if (current != null && Objects.equals(current.identity(), rejectedIdentity)) {
            cached.compareAndSet(current, null);
        }
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
