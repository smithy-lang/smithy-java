/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.FeatureId;
import software.amazon.smithy.java.context.Context;

class FeatureIdTest {

    @Test
    void successfulProviderEmitsFeatureId() {
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        provider("env",
                                StandardProvider.ENVIRONMENT,
                                Set.of(new CredentialFeatureId("g")),
                                staticResolver("AK", "SK"))),
                null);

        Context ctx = Context.create();
        ctx.put(CallContext.FEATURE_IDS, new HashSet<>());

        chain.resolveIdentity(ctx);

        Set<FeatureId> ids = ctx.get(CallContext.FEATURE_IDS);
        assertEquals(1, ids.size());
        assertEquals("g", ids.iterator().next().getShortName());
    }

    @Test
    void failedProviderDoesNotEmitFeatureId() {
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        provider("env",
                                StandardProvider.ENVIRONMENT,
                                Set.of(new CredentialFeatureId("g")),
                                errorResolver("no creds")),
                        provider("profile",
                                StandardProvider.SHARED_CONFIG,
                                Set.of(new CredentialFeatureId("n")),
                                staticResolver("AK", "SK"))),
                null);

        Context ctx = Context.create();
        ctx.put(CallContext.FEATURE_IDS, new HashSet<>());

        chain.resolveIdentity(ctx);

        Set<FeatureId> ids = ctx.get(CallContext.FEATURE_IDS);
        assertEquals(1, ids.size());
        assertEquals("n", ids.iterator().next().getShortName());
    }

    @Test
    void multipleFeatureIdsEmitted() {
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        provider("proc",
                                StandardProvider.SHARED_CONFIG,
                                Set.of(new CredentialFeatureId("v"), new CredentialFeatureId("w")),
                                staticResolver("AK", "SK"))),
                null);

        Context ctx = Context.create();
        ctx.put(CallContext.FEATURE_IDS, new HashSet<>());

        chain.resolveIdentity(ctx);

        Set<FeatureId> ids = ctx.get(CallContext.FEATURE_IDS);
        assertEquals(2, ids.size());
        Set<String> names = new HashSet<>();
        for (FeatureId id : ids) {
            names.add(id.getShortName());
        }
        assertTrue(names.contains("v"));
        assertTrue(names.contains("w"));
    }

    @Test
    void noFeatureIdsWhenContextKeyNotSet() {
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        provider("env",
                                StandardProvider.ENVIRONMENT,
                                Set.of(new CredentialFeatureId("g")),
                                staticResolver("AK", "SK"))),
                null);

        // No FEATURE_IDS in context — should not throw.
        Context ctx = Context.create();
        var result = chain.resolveIdentity(ctx);
        assertEquals("AK", result.identity().accessKeyId());
    }

    private static ChainIdentityProvider provider(
            String name,
            StandardProvider slot,
            Set<CredentialFeatureId> featureIds,
            IdentityResolver<AwsCredentialsIdentity> resolver
    ) {
        return new ChainIdentityProvider() {
            public String name() {
                return name;
            }

            public Set<CredentialFeatureId> featureIds() {
                return featureIds;
            }

            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(slot);
            }

            @SuppressWarnings("unchecked")
            public void create(Class<? extends Identity> identityType, ChainSetup setup) {
                setup.addResolver(resolver);
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> staticResolver(String ak, String sk) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.of(AwsCredentialsIdentity.create(ak, sk));
        return new IdentityResolver<>() {
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> errorResolver(String msg) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.ofError(FeatureIdTest.class, msg);
        return new IdentityResolver<>() {
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }
}
