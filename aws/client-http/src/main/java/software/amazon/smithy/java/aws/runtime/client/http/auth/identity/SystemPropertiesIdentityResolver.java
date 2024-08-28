/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

<<<<<<< HEAD:aws/client-http/src/main/java/software/amazon/smithy/java/aws/runtime/client/http/auth/identity/SystemPropertiesIdentityResolver.java
package software.amazon.smithy.java.aws.runtime.client.http.auth.identity;
=======
package software.amazon.smithy.java.runtime.aws.http.auth.identity;
>>>>>>> 95767d49 (Add default resolvers):aws-client-http/src/main/java/software/amazon/smithy/java/runtime/aws/http/auth/identity/SystemPropertiesIdentityResolver.java

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityNotFoundException;

<<<<<<< HEAD:aws/client-http/src/main/java/software/amazon/smithy/java/aws/runtime/client/http/auth/identity/SystemPropertiesIdentityResolver.java
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
=======
public class SystemPropertiesIdentityResolver implements AwsCredentialsResolver {
>>>>>>> 95767d49 (Add default resolvers):aws-client-http/src/main/java/software/amazon/smithy/java/runtime/aws/http/auth/identity/SystemPropertiesIdentityResolver.java
    private static final String ACCESS_KEY_PROPERTY = "aws.accessKeyId";
    private static final String SECRET_KEY_PROPERTY = "aws.secretAccessKey";
    private static final String SESSION_TOKEN_PROPERTY = "aws.sessionToken";

    @Override
    public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(AuthProperties requestProperties) {
        String accessKey = System.getProperty(ACCESS_KEY_PROPERTY);
        String secretKey = System.getProperty(SECRET_KEY_PROPERTY);
        String sessionToken = System.getProperty(SESSION_TOKEN_PROPERTY);

        if (accessKey == null || secretKey == null) {
            return CompletableFuture.failedFuture(
                new IdentityNotFoundException(
                    "Could not find access and secret key system properties",
                    SystemPropertiesIdentityResolver.class,
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
