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
 * {@link AwsCredentialsResolver} implementation that loads credentials from Java system properties.
 *
 * <p>This resolver reads its source once on first access and caches the result. Call
 * {@link #invalidate(AwsCredentialsIdentity)} with the cached identity to force re-reading (e.g., in tests).
 *
 * <p>Expected system properties:
 * <dl>
 *     <dt>{@code aws.accessKeyId}</dt>
 *     <dd>Sets the AWS Access Key for the identity</dd>
 *     <dt>{@code aws.secretAccessKey}</dt>
 *     <dd>Sets the AWS Secret Key for the identity</dd>
 *     <dt>{@code aws.sessionToken}</dt>
 *     <dd>(optional) Security token provided by the AWS Security Token Service (STS) for temporary credentials</dd>
 *     <dt>{@code aws.accountId}</dt>
 *     <dd>(optional) AWS account ID</dd>
 * </dl>
 *
 * @see <a href="https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html">Java System Properties</a>
 */
public final class SystemPropertiesIdentityResolver implements AwsCredentialsResolver {
    public static final SystemPropertiesIdentityResolver INSTANCE = new SystemPropertiesIdentityResolver();

    static final String ACCESS_KEY_PROPERTY = "aws.accessKeyId";
    static final String SECRET_KEY_PROPERTY = "aws.secretAccessKey";
    static final String SESSION_TOKEN_PROPERTY = "aws.sessionToken";
    static final String ACCOUNT_ID_PROPERTY = "aws.accountId";
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND = IdentityResult.ofError(
            SystemPropertiesIdentityResolver.class,
            "Could not resolve AWS identity from the aws.accessKeyId and aws.secretAccessKey system properties");

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
        String accessKey = System.getProperty(ACCESS_KEY_PROPERTY);
        String secretKey = System.getProperty(SECRET_KEY_PROPERTY);
        if (accessKey == null || secretKey == null) {
            return NOT_FOUND;
        }

        String sessionToken = System.getProperty(SESSION_TOKEN_PROPERTY);
        String accountId = System.getProperty(ACCOUNT_ID_PROPERTY);
        return IdentityResult.of(AwsCredentialsIdentity.create(accessKey, secretKey, sessionToken, null, accountId));
    }
}
