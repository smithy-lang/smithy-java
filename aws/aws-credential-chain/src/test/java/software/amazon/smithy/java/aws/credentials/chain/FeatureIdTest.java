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
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.FeatureId;
import software.amazon.smithy.java.context.Context;

class FeatureIdTest {

    @Test
    void successfulProviderEmitsFeatureId() {
        var chain = AwsCredentialChain.assemble(List.of(
                provider("env",
                        BuiltinProvider.ENVIRONMENT,
                        Set.of(new CredentialFeatureId("g")),
                        staticResolver("AK", "SK"))));

        Context ctx = Context.create();
        ctx.put(CallContext.FEATURE_IDS, new HashSet<>());

        chain.resolveIdentity(ctx);

        Set<FeatureId> ids = ctx.get(CallContext.FEATURE_IDS);
        assertEquals(1, ids.size());
        assertEquals("g", ids.iterator().next().getShortName());
    }

    @Test
    void failedProviderDoesNotEmitFeatureId() {
        var chain = AwsCredentialChain.assemble(List.of(
                provider("env",
                        BuiltinProvider.ENVIRONMENT,
                        Set.of(new CredentialFeatureId("g")),
                        errorResolver("no creds")),
                provider("profile",
                        BuiltinProvider.SHARED_CONFIG,
                        Set.of(new CredentialFeatureId("n")),
                        staticResolver("AK", "SK"))));

        Context ctx = Context.create();
        ctx.put(CallContext.FEATURE_IDS, new HashSet<>());

        chain.resolveIdentity(ctx);

        Set<FeatureId> ids = ctx.get(CallContext.FEATURE_IDS);
        assertEquals(1, ids.size());
        assertEquals("n", ids.iterator().next().getShortName());
    }

    @Test
    void multipleFeatureIdsEmitted() {
        var chain = AwsCredentialChain.assemble(List.of(
                provider("proc",
                        BuiltinProvider.SHARED_CONFIG,
                        Set.of(new CredentialFeatureId("v"), new CredentialFeatureId("w")),
                        staticResolver("AK", "SK"))));

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
        var chain = AwsCredentialChain.assemble(List.of(
                provider("env",
                        BuiltinProvider.ENVIRONMENT,
                        Set.of(new CredentialFeatureId("g")),
                        staticResolver("AK", "SK"))));

        // No FEATURE_IDS in context — should not throw.
        Context ctx = Context.create();
        var result = chain.resolveIdentity(ctx);
        assertEquals("AK", result.identity().accessKeyId());
    }

    private static AwsCredentialProvider provider(
            String name,
            BuiltinProvider slot,
            Set<CredentialFeatureId> featureIds,
            IdentityResolver<AwsCredentialsIdentity> resolver
    ) {
        return new AwsCredentialProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Set<CredentialFeatureId> featureIds() {
                return featureIds;
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Builtin(slot);
            }

            @Override
            public IdentityResolver<AwsCredentialsIdentity> create(ProviderContext context) {
                return resolver;
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> staticResolver(String ak, String sk) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.of(AwsCredentialsIdentity.create(ak, sk));
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> errorResolver(String msg) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.ofError(FeatureIdTest.class, msg);
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }
}
