/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.shapes.ShapeId;

class AwsCredentialChainPluginTest {

    private static final AuthScheme<Object, AwsCredentialsIdentity> SIGV4 = AuthScheme.of(
            ShapeId.from("aws.auth#sigv4"),
            Object.class,
            AwsCredentialsIdentity.class,
            (request, identity, properties) -> new SignResult<>(request));

    @Test
    void installsDefaultChainWhenNoCredentialsResolverIsRegistered() {
        var config = ClientConfig.builder().putSupportedAuthSchemes(SIGV4);

        new AwsCredentialChainPlugin().configureClient(config);

        assertEquals(1, config.identityResolvers().size());
        assertEquals(AwsCredentialsIdentity.class, config.identityResolvers().get(0).identityType());
        assertTrue(config.interceptors().contains(InvalidateCredentialsInterceptor.INSTANCE));
    }

    @Test
    void honorsStaticCredentialsRegisteredViaIdentityResolverOf() {
        // IdentityResolver.of reports the hidden record class, not AwsCredentialsIdentity.
        var userResolver = IdentityResolver.of(AwsCredentialsIdentity.create("AKID", "secret"));
        var config = ClientConfig.builder()
                .putSupportedAuthSchemes(SIGV4)
                .addIdentityResolver(userResolver);

        new AwsCredentialChainPlugin().configureClient(config);

        assertEquals(List.of(userResolver), config.identityResolvers());
        var resolved = IdentityResolvers.of(config.identityResolvers()).identityResolver(AwsCredentialsIdentity.class);
        assertSame(userResolver, resolved);
        assertEquals("AKID", resolved.resolveIdentity(Context.empty()).unwrap().accessKeyId());
    }

    @Test
    void honorsResolverTypedAsCredentialsSubtype() {
        var userResolver = new SessionCredentialsResolver();
        var config = ClientConfig.builder()
                .putSupportedAuthSchemes(SIGV4)
                .addIdentityResolver(userResolver);

        new AwsCredentialChainPlugin().configureClient(config);

        assertEquals(List.of(userResolver), config.identityResolvers());
        assertSame(userResolver,
                IdentityResolvers.of(config.identityResolvers()).identityResolver(AwsCredentialsIdentity.class));
    }

    @Test
    void doesNothingWithoutAwsAuthScheme() {
        var config = ClientConfig.builder().putSupportedAuthSchemes(AuthScheme.noAuthAuthScheme());

        new AwsCredentialChainPlugin().configureClient(config);

        assertTrue(config.identityResolvers().isEmpty());
        assertTrue(config.interceptors().isEmpty());
    }

    private interface SessionCredentials extends AwsCredentialsIdentity {}

    private static final class SessionCredentialsResolver implements IdentityResolver<SessionCredentials> {
        @Override
        public IdentityResult<SessionCredentials> resolveIdentity(Context requestProperties) {
            return IdentityResult.of(new SessionCredentials() {
                @Override
                public String accessKeyId() {
                    return "AKID";
                }

                @Override
                public String secretAccessKey() {
                    return "secret";
                }
            });
        }

        @Override
        public Class<SessionCredentials> identityType() {
            return SessionCredentials.class;
        }
    }
}
