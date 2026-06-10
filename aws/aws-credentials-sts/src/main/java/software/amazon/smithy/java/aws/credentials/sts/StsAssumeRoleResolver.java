/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
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
final class StsAssumeRoleResolver implements IdentityResolver<AwsCredentialsIdentity> {

    private final AwsConfigCredentialSource.AssumeRole source;
    private final AwsProfileFile profileFile;
    private final StsEndpointConfig endpoint;

    StsAssumeRoleResolver(
            AwsConfigCredentialSource.AssumeRole source,
            AwsProfileFile profileFile,
            StsEndpointConfig endpoint
    ) {
        this.source = source;
        this.profileFile = profileFile;
        this.endpoint = endpoint;
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
        AwsCredentialsIdentity sourceCredentials = resolveSourceCredentials(source, new HashSet<>());
        return callAssumeRole(sourceCredentials, source.roleArn(), source.externalId());
    }

    private AwsCredentialsIdentity resolveSourceCredentials(
            AwsConfigCredentialSource.AssumeRole ar,
            Set<String> visited
    ) {
        if (ar.sourceProfile() != null) {
            return resolveFromSourceProfile(ar.sourceProfile(), visited);
        } else if (ar.credentialSource() != null) {
            return resolveFromCredentialSource(ar.credentialSource());
        }
        throw new IllegalStateException("Profile with role_arn must have either source_profile or credential_source");
    }

    private AwsCredentialsIdentity resolveFromSourceProfile(String profileName, Set<String> visited) {
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
                return AwsCredentialsIdentity.create(
                        accessKeyId,
                        secretAccessKey,
                        null,
                        null,
                        accountId);
            } else if (src instanceof AwsConfigCredentialSource.SessionKeys(String accessKeyId, String secretAccessKey, String sessionToken, String accountId)) {
                return AwsCredentialsIdentity.create(
                        accessKeyId,
                        secretAccessKey,
                        sessionToken,
                        null,
                        accountId);
            } else if (src instanceof AwsConfigCredentialSource.AssumeRole nested) {
                // Recursive: resolve source creds for the nested role, then assume it
                AwsCredentialsIdentity nestedSource = resolveSourceCredentials(nested, visited);
                return callAssumeRole(nestedSource, nested.roleArn(), nested.externalId()).unwrap();
            }
        }

        throw new IllegalStateException("Source profile '" + profileName + "' has no resolvable credential source");
    }

    private AwsCredentialsIdentity resolveFromCredentialSource(String credentialSource) {
        return switch (credentialSource) {
            case "Environment" -> {
                String ak = getRequireEnv("AWS_ACCESS_KEY_ID");
                String sk = getRequireEnv("AWS_SECRET_ACCESS_KEY");
                String st = System.getenv("AWS_SESSION_TOKEN");
                yield AwsCredentialsIdentity.create(ak, sk, st, null, System.getenv("AWS_ACCOUNT_ID"));
            }
            case "Ec2InstanceMetadata" -> {
                // Create a temporary ChainSetup to let ImdsCredentialProvider register its resolver
                var tempSetup = ChainSetup.builder().build();
                new ImdsCredentialProvider().setup(AwsCredentialsIdentity.class, tempSetup);
                var resolvers = tempSetup.resolvers();
                if (resolvers.isEmpty()) {
                    throw new IllegalStateException("IMDS credential provider did not produce a resolver");
                }
                @SuppressWarnings("unchecked")
                var r = (IdentityResolver<AwsCredentialsIdentity>) resolvers.getFirst().resolver();
                yield r.resolveIdentity(Context.create()).unwrap();
            }
            default -> throw new IllegalStateException("Unsupported credential_source: " + credentialSource);
        };
    }

    private static String getRequireEnv(String name) {
        var result = System.getenv(name);
        if (result == null) {
            throw new IllegalStateException("credential_source=Environment but " + name + " not set");
        }
        return result;
    }

    private IdentityResult<AwsCredentialsIdentity> callAssumeRole(
            AwsCredentialsIdentity sourceCredentials,
            String roleArn,
            String externalId
    ) {
        // Create a static resolver for the source credentials
        var sourceResolver = createSourceResolver(sourceCredentials);

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

    private static IdentityResolver<AwsCredentialsIdentity> createSourceResolver(AwsCredentialsIdentity creds) {
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
        };
    }
}
