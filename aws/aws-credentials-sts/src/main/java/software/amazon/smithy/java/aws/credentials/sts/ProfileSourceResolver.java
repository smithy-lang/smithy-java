/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.credentials.chain.AwsCredentialCaching;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;

/**
 * Builds the immutable source-resolver path for a profile assume-role provider.
 */
final class ProfileSourceResolver {
    private ProfileSourceResolver() {}

    static IdentityResolver<AwsCredentialsIdentity> resolve(
            AwsConfigCredentialSource.AssumeRole role,
            String selectedProfileName,
            ChainSetup setup
    ) {
        return resolveRoleSource(role, selectedProfileName, setup, List.of(selectedProfileName));
    }

    private static IdentityResolver<AwsCredentialsIdentity> resolveRoleSource(
            AwsConfigCredentialSource.AssumeRole role,
            String currentProfileName,
            ChainSetup setup,
            List<String> path
    ) {
        if (role.sourceProfile() != null && role.credentialSource() != null) {
            throw new IllegalStateException(
                    "Profile with role_arn cannot contain both source_profile and credential_source");
        }
        if (role.sourceProfile() != null) {
            if (role.sourceProfile().equals(currentProfileName)) {
                var resolver = keyResolver(requireProfile(setup, currentProfileName));
                if (resolver == null) {
                    throw new IllegalStateException(
                            "Self-referencing profile '" + currentProfileName
                                    + "' must contain complete static or session credentials");
                }
                return resolver;
            }
            return resolveSourceProfile(role.sourceProfile(), setup, path);
        }
        if (role.credentialSource() != null) {
            return setup.sourceResolver(role.credentialSource(), AwsCredentialsIdentity.class);
        }
        throw new IllegalStateException("Profile with role_arn must have either source_profile or credential_source");
    }

    private static IdentityResolver<AwsCredentialsIdentity> resolveSourceProfile(
            String profileName,
            ChainSetup setup,
            List<String> path
    ) {
        if (path.contains(profileName)) {
            var cycle = new ArrayList<>(path);
            cycle.add(profileName);
            throw new IllegalStateException(
                    "Circular source_profile reference detected: " + String.join(" -> ", cycle));
        }

        AwsProfile profile = requireProfile(setup, profileName);

        // A nested source profile terminates at keys even when it also contains role configuration.
        var keyResolver = keyResolver(profile);
        if (keyResolver != null) {
            return keyResolver;
        }

        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.AssumeRole nested) {
                var nextPath = new ArrayList<>(path);
                nextPath.add(profileName);
                var nestedSource = resolveRoleSource(nested, profileName, setup, List.copyOf(nextPath));
                var endpoint = StsEndpointConfig.resolve(nested.region(), setup);
                var nestedResolver = new StsAssumeRoleResolver(nested, endpoint, nestedSource);
                return AwsCredentialCaching.staticallyStable(nestedResolver, setup.executor());
            }
        }

        throw new IllegalStateException("Source profile '" + profileName + "' has no resolvable credential source");
    }

    private static AwsProfile requireProfile(ChainSetup setup, String profileName) {
        if (setup.profileFile() == null) {
            throw new IllegalStateException("No profile file available for source_profile resolution");
        }
        AwsProfile profile = setup.profileFile().profile(profileName);
        if (profile == null) {
            throw new IllegalStateException("Source profile '" + profileName + "' not found");
        }
        return profile;
    }

    private static IdentityResolver<AwsCredentialsIdentity> keyResolver(AwsProfile profile) {
        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.SessionKeys keys) {
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        keys.accessKeyId(),
                        keys.secretAccessKey(),
                        keys.sessionToken(),
                        null,
                        keys.accountId()));
            }
        }
        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.StaticKeys keys) {
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        keys.accessKeyId(),
                        keys.secretAccessKey(),
                        null,
                        null,
                        keys.accountId()));
            }
        }
        return null;
    }
}
