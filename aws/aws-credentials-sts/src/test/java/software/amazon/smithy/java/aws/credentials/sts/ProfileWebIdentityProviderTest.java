/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;

class ProfileWebIdentityProviderTest {

    @Test
    void registersWhenProfileHasTokenFile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                web_identity_token_file = /some/path
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile());
        var provider = new ProfileWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(1, setup.resolvers().size());
    }

    @Test
    void skipsForNonAwsIdentity(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                web_identity_token_file = /some/path
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile());
        var provider = new ProfileWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(software.amazon.smithy.java.auth.api.identity.Identity.class, setup);
        assertEquals(0, setup.resolvers().size());
    }

    @Test
    void skipsWhenNoTokenFile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile());
        var provider = new ProfileWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(0, setup.resolvers().size());
    }
}
