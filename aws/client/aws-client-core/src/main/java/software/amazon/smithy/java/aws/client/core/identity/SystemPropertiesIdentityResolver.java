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
 * {@link AwsCredentialsResolver} implementation that loads credentials from Java system properties.
 *
 * <p>This resolver reads system properties once on first access and caches the result. Use
 * {@link #invalidate()} to force re-reading (e.g., in tests).
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

    private static final String ACCESS_KEY_PROPERTY = "aws.accessKeyId";
    private static final String SECRET_KEY_PROPERTY = "aws.secretAccessKey";
    private static final String SESSION_TOKEN_PROPERTY = "aws.sessionToken";
    private static final String ACCOUNT_ID_PROPERTY = "aws.accountId";
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND = IdentityResult.ofError(
            SystemPropertiesIdentityResolver.class,
            "Could not resolve AWS identity from the aws.accessKeyId and aws.secretAccessKey system properties");

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
