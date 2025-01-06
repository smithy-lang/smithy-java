/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResult;

/**
 * {@link AwsCredentialsResolver} implementation that loads credentials from Java system properties.
 *
 * <p>This resolvers expects the following system properties to be present in order to resolve an
 * {@link AwsCredentialsIdentity}:
 * <dl>
 *     <dt>{@code aws.accessKeyId}</dt>
 *     <dd>Sets the AWS Access Key for the identity</dd>
 *     <dt>{@code aws.secretAccessKey}</dt>
 *     <dd>Sets the AWS Secret Key for the identity</dd>
 *     <dt>{@code aws.sessionToken}</dt>
 *     <dd>(optional) Security token provided by the AWS Security Token Service (STS) for temporary credentials</dd>
 * </dl>
 *
 * @see <a href="https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html">Java System Properties</a>
 */
public final class SystemPropertiesIdentityResolver implements AwsCredentialsResolver {
    private static final String ACCESS_KEY_PROPERTY = "aws.accessKeyId";
    private static final String SECRET_KEY_PROPERTY = "aws.secretAccessKey";
    private static final String SESSION_TOKEN_PROPERTY = "aws.sessionToken";
    private static final String ERROR_MESSAGE = "Could not resolve AWS identity from the aws.accessKeyId and "
            + "aws.secretAccessKey system properties";

    @Override
    public CompletableFuture<IdentityResult<AwsCredentialsIdentity>> resolveIdentity(AuthProperties requestProperties) {
        String accessKey = System.getProperty(ACCESS_KEY_PROPERTY);
        String secretKey = System.getProperty(SECRET_KEY_PROPERTY);
        String sessionToken = System.getProperty(SESSION_TOKEN_PROPERTY);

        if (accessKey != null && secretKey != null) {
            return CompletableFuture.completedFuture(
                    IdentityResult.of(
                            AwsCredentialsIdentity.create(
                                    accessKey,
                                    secretKey,
                                    sessionToken)));
        }

        return CompletableFuture.completedFuture(IdentityResult.ofError(getClass(), ERROR_MESSAGE));
    }
}
