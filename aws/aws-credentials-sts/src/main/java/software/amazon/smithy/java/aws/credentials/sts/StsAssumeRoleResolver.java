/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.AwsCredentialCaching;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.imds.ImdsCredentialProvider;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.dynamicclient.DynamicClient;

/**
 * Resolver that calls STS AssumeRole using source credentials resolved from
 * a source_profile chain or credential_source.
 *
 * <p>Handles recursive source_profile resolution with cycle detection per the
 * Assume Role SEP.
 */
final class StsAssumeRoleResolver implements IdentityResolver<AwsCredentialsIdentity>, AutoCloseable {

    private final AwsConfigCredentialSource.AssumeRole source;
    private final AwsProfileFile profileFile;
    private final StsEndpointConfig endpoint;
    private final ScheduledExecutorService executor;
    private final Set<String> sourceProfilePath;
    private final Function<String, String> environment;
    private volatile IdentityResolver<AwsCredentialsIdentity> sourceResolver;

    StsAssumeRoleResolver(
            AwsConfigCredentialSource.AssumeRole source,
            AwsProfileFile profileFile,
            StsEndpointConfig endpoint,
            ScheduledExecutorService executor,
            Function<String, String> environment
    ) {
        this(source, profileFile, endpoint, executor, Set.of(), environment);
    }

    private StsAssumeRoleResolver(
            AwsConfigCredentialSource.AssumeRole source,
            AwsProfileFile profileFile,
            StsEndpointConfig endpoint,
            ScheduledExecutorService executor,
            Set<String> sourceProfilePath,
            Function<String, String> environment
    ) {
        this.source = source;
        this.profileFile = profileFile;
        this.endpoint = endpoint;
        this.executor = executor;
        this.sourceProfilePath = Set.copyOf(sourceProfilePath);
        this.environment = environment;
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    // Visible for testing: the STS endpoint configuration this resolver was assembled with.
    StsEndpointConfig endpoint() {
        return endpoint;
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
        var sourceResolver = sourceResolver();
        AwsCredentialsIdentity sourceCredentials = sourceResolver.resolveIdentity(ctx).unwrap();
        return callAssumeRole(sourceResolver, sourceCredentials, source.roleArn(), source.externalId());
    }

    IdentityResolver<AwsCredentialsIdentity> sourceResolver() {
        IdentityResolver<AwsCredentialsIdentity> current = sourceResolver;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sourceResolver == null) {
                sourceResolver = createSourceResolver(source, new HashSet<>(sourceProfilePath));
            }
            return sourceResolver;
        }
    }

    private IdentityResolver<AwsCredentialsIdentity> createSourceResolver(
            AwsConfigCredentialSource.AssumeRole ar,
            Set<String> visited
    ) {
        if (ar.sourceProfile() != null) {
            return createSourceProfileResolver(ar.sourceProfile(), visited);
        } else if (ar.credentialSource() != null) {
            return createCredentialSourceResolver(ar.credentialSource());
        }
        throw new IllegalStateException("Profile with role_arn must have either source_profile or credential_source");
    }

    private IdentityResolver<AwsCredentialsIdentity> createSourceProfileResolver(
            String profileName,
            Set<String> visited
    ) {
        if (!visited.add(profileName)) {
            throw new IllegalStateException("Circular source_profile reference detected: " + visited);
        } else if (profileFile == null) {
            throw new IllegalStateException("No profile file available for source_profile resolution");
        }

        AwsProfile sourceProfile = profileFile.profile(profileName);
        if (sourceProfile == null) {
            throw new IllegalStateException("Source profile '" + profileName + "' not found");
        }

        // Per the Assume Role SEP: terminate at static credentials
        for (AwsConfigCredentialSource src : sourceProfile.credentialSources()) {
            if (src instanceof AwsConfigCredentialSource.StaticKeys(String accessKeyId, String secretAccessKey, String accountId)) {
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        accessKeyId,
                        secretAccessKey,
                        null,
                        null,
                        accountId));
            } else if (src instanceof AwsConfigCredentialSource.SessionKeys(String accessKeyId, String secretAccessKey, String sessionToken, String accountId)) {
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        accessKeyId,
                        secretAccessKey,
                        sessionToken,
                        null,
                        accountId));
            } else if (src instanceof AwsConfigCredentialSource.AssumeRole nested) {
                var nestedResolver =
                        new StsAssumeRoleResolver(nested, profileFile, endpoint, executor, visited, environment);
                return AwsCredentialCaching.staticallyStable(nestedResolver, executor);
            }
        }

        throw new IllegalStateException("Source profile '" + profileName + "' has no resolvable credential source");
    }

    private IdentityResolver<AwsCredentialsIdentity> createCredentialSourceResolver(String credentialSource) {
        return switch (credentialSource) {
            case "Environment" -> {
                String ak = getRequireEnv("AWS_ACCESS_KEY_ID");
                String sk = getRequireEnv("AWS_SECRET_ACCESS_KEY");
                String st = environment.apply("AWS_SESSION_TOKEN");
                yield IdentityResolver.of(
                        AwsCredentialsIdentity.create(ak, sk, st, null, environment.apply("AWS_ACCOUNT_ID")));
            }
            case "Ec2InstanceMetadata" -> {
                var tempSetup = ChainSetup.builder().executor(executor).env(environment).build();
                var provider = new ImdsCredentialProvider();
                tempSetup.setCurrentProvider(provider);
                provider.setup(AwsCredentialsIdentity.class, tempSetup);
                var resolvers = tempSetup.resolvers();
                if (resolvers.isEmpty()) {
                    throw new IllegalStateException("IMDS credential provider did not produce a resolver");
                }
                @SuppressWarnings("unchecked")
                var resolver = (IdentityResolver<AwsCredentialsIdentity>) resolvers.getFirst().resolver();
                yield resolver;
            }
            default -> throw new IllegalStateException("Unsupported credential_source: " + credentialSource);
        };
    }

    private String getRequireEnv(String name) {
        var result = environment.apply(name);
        if (result == null) {
            throw new IllegalStateException("credential_source=Environment but " + name + " not set");
        }
        return result;
    }

    @Override
    public void close() {
        IdentityResolver<AwsCredentialsIdentity> current = sourceResolver;
        if (current instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception error) {
                throw new IllegalStateException("Failed to close source credential resolver", error);
            }
        }
    }

    private IdentityResult<AwsCredentialsIdentity> callAssumeRole(
            IdentityResolver<AwsCredentialsIdentity> retainedSourceResolver,
            AwsCredentialsIdentity sourceCredentials,
            String roleArn,
            String externalId
    ) {
        var sourceResolver = createSourceResolver(sourceCredentials, retainedSourceResolver);

        try (DynamicClient client = StsClientFactory.create(sourceResolver, endpoint)) {
            // ExternalId is optional; Map.of rejects null values, so only include it when present.
            Map<String, Object> input = new HashMap<>();
            input.put("RoleArn", roleArn);
            input.put("RoleSessionName", "smithy-java-" + System.currentTimeMillis());
            if (externalId != null) {
                input.put("ExternalId", externalId);
            }
            return StsWebIdentityResolver.parseCredentials(client.call("AssumeRole", input));
        }
    }

    static IdentityResolver<AwsCredentialsIdentity> createSourceResolver(
            AwsCredentialsIdentity creds,
            IdentityResolver<AwsCredentialsIdentity> retainedSourceResolver
    ) {
        IdentityResult<AwsCredentialsIdentity> sourceResult = IdentityResult.of(creds);
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context c) {
                return sourceResult;
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }

            @Override
            public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
                retainedSourceResolver.invalidate(rejectedIdentity);
            }
        };
    }
}
