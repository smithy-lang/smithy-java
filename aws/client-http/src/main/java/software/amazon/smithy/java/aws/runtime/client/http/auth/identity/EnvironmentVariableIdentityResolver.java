/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.identity;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.client.auth.api.identity.IdentityNotFoundException;


/**
 * {@link AwsCredentialsResolver} implementation that loads credentials from environment variables.
 *
 * <p>This resolvers expects the following environment variables to be present in order to resolve an
 * {@link AwsCredentialsIdentity}:
 * <dl>
 *     <dt>{@code AWS_ACCESS_KEY_ID}</dt>
 *     <dd>Sets the AWS Access Key for the identity</dd>
 *     <dt>{@code AWS_SECRET_ACCESS_KEY}</dt>
 *     <dd>Sets the AWS Secret Key for the identity</dd>
 *     <dt>{@code AWS_SESSION_TOKEN}</dt>
 *     <dd>(optional) Security token provided by the AWS Security Token Service (STS) for temporary credentials</dd>
 * </dl>
 */
public final class EnvironmentVariableIdentityResolver implements AwsCredentialsResolver {
    private static final String ACCESS_KEY_PROPERTY = "AWS_ACCESS_KEY_ID";
    private static final String SECRET_KEY_PROPERTY = "AWS_SECRET_ACCESS_KEY";
    private static final String SESSION_TOKEN_PROPERTY = "AWS_SESSION_TOKEN";

    @Override
    public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(AuthProperties requestProperties) {
        String accessKey = System.getenv(ACCESS_KEY_PROPERTY);
        String secretKey = System.getenv(SECRET_KEY_PROPERTY);
        String sessionToken = System.getenv(SESSION_TOKEN_PROPERTY);

        if (accessKey == null || secretKey == null) {
            return CompletableFuture.failedFuture(
                new IdentityNotFoundException(
                    "Could not find access and secret key environment variables",
                    EnvironmentVariableIdentityResolver.class,
                    AwsCredentialsIdentity.class
                )
            );
        }
        return CompletableFuture.completedFuture(
            AwsCredentialsIdentity.create(
                accessKey,
                secretKey,
                sessionToken
            )
        );
    }
}
