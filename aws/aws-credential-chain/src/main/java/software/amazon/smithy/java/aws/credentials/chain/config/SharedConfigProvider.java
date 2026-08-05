/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

/**
 * Claims the {@link software.amazon.smithy.java.aws.credentials.chain.StandardProvider#SHARED_CONFIG}
 * slot. Parses the AWS config/credentials files and stores the result on the {@link ChainSetup}
 * for downstream providers. Does not register any resolver.
 */
public final class SharedConfigProvider implements ChainIdentityProvider {

    @Override
    public String name() {
        return "SharedConfig";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG);
    }

    @Override
    public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
        // Defer to a profile file already supplied on the setup (e.g., injected by the client builder or an
        // upstream provider) instead of reading from disk. Only fall back to loading the shared config/credentials
        // files when none was provided.
        AwsProfileFile profileFile = setup.profileFile();
        if (profileFile == null) {
            profileFile = AwsProfileFile.loadSilently();
        }
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            String name = setup.profileNameOverride();
            if (name != null) {
                setup.setProfile(profileFile.profile(name));
            } else {
                setup.setProfile(profileFile.activeProfile(setup::getenv));
            }
            markDetectedProfileSources(setup);
        }
    }

    private static void markDetectedProfileSources(ChainSetup setup) {
        if (setup.profile() == null) {
            return;
        }
        for (AwsConfigCredentialSource source : setup.profile().credentialSources()) {
            setup.markDetected(slot(source));
        }
    }

    private static StandardProvider slot(AwsConfigCredentialSource source) {
        return switch (source) {
            case AwsConfigCredentialSource.StaticKeys ignored -> StandardProvider.PROFILE_STATIC_KEYS;
            case AwsConfigCredentialSource.SessionKeys ignored -> StandardProvider.PROFILE_SESSION_KEYS;
            case AwsConfigCredentialSource.AssumeRole ignored -> StandardProvider.PROFILE_ASSUME_ROLE;
            case AwsConfigCredentialSource.WebIdentityToken ignored -> StandardProvider.PROFILE_WEB_IDENTITY;
            case AwsConfigCredentialSource.SsoSession ignored -> StandardProvider.PROFILE_SSO_SESSION;
            case AwsConfigCredentialSource.LegacySso ignored -> StandardProvider.PROFILE_LEGACY_SSO;
            case AwsConfigCredentialSource.CredentialProcess ignored -> StandardProvider.PROFILE_CREDENTIAL_PROCESS;
            case AwsConfigCredentialSource.LoginSession ignored -> StandardProvider.PROFILE_LOGIN;
        };
    }
}
