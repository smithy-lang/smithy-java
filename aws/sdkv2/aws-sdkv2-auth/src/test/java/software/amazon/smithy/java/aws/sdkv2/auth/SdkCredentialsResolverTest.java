/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

public class SdkCredentialsResolverTest {

    private static final String ACCESS_KEY = "AccessKey";
    private static final String SECRET_KEY = "SecretKey";
    private static final String SESSION_TOKEN = "SessionToken";
    private static final Instant EXPIRATION_TIME = Instant.now().plusSeconds(10);

    private final AuthProperties authProperties = null;

    @Test
    public void testResolveBasicCredentials() throws ExecutionException, InterruptedException {
        var basicCredentials = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        var credentialsProvider = StaticCredentialsProvider.create(basicCredentials);

        var resolver = new SdkCredentialsResolver(credentialsProvider);

        var result = resolver.resolveIdentity(authProperties).get();

        assertNotNull(result);
        AwsCredentialsIdentity identity = result.identity();
        assertNotNull(identity);
        assertEquals(ACCESS_KEY, identity.accessKeyId());
        assertEquals(SECRET_KEY, identity.secretAccessKey());
        assertNull(identity.sessionToken());
        assertNull(identity.expirationTime());
    }

    @Test
    public void testResolveSessionCredentialsWithExpiration() throws ExecutionException, InterruptedException {
        var sessionCredentials = AwsSessionCredentials.builder()
                .sessionToken(SESSION_TOKEN)
                .accessKeyId(ACCESS_KEY)
                .secretAccessKey(SECRET_KEY)
                .expirationTime(EXPIRATION_TIME)
                .build();
        var credentialsProvider = StaticCredentialsProvider.create(sessionCredentials);

        var resolver = new SdkCredentialsResolver(credentialsProvider);

        IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(authProperties).get();

        assertNotNull(result);
        var identity = result.identity();
        assertNotNull(identity);
        assertEquals(ACCESS_KEY, identity.accessKeyId());
        assertEquals(SECRET_KEY, identity.secretAccessKey());
        assertEquals(SESSION_TOKEN, identity.sessionToken());
        assertEquals(EXPIRATION_TIME, identity.expirationTime());
    }

    @Test
    public void testResolveSessionCredentialsWithoutExpiration() throws ExecutionException, InterruptedException {
        var sessionCredentials = AwsSessionCredentials.create(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN);
        var credentialsProvider = StaticCredentialsProvider.create(sessionCredentials);

        var resolver = new SdkCredentialsResolver(credentialsProvider);

        IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(authProperties).get();

        assertNotNull(result);
        AwsCredentialsIdentity identity = result.identity();
        assertNotNull(identity);
        assertEquals(ACCESS_KEY, identity.accessKeyId());
        assertEquals(SECRET_KEY, identity.secretAccessKey());
        assertEquals(SESSION_TOKEN, identity.sessionToken());
        assertNull(identity.expirationTime());
    }
}
