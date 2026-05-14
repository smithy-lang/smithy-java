/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;

class EnvWebIdentityProviderTest {

    @Test
    void registersWhenEnvVarsSet(@TempDir Path tmp) throws IOException {
        Path tokenFile = tmp.resolve("token");
        Files.writeString(tokenFile, "my-web-identity-token");

        var setup = ChainSetup.builder()
                .env(Map.of(
                        "AWS_WEB_IDENTITY_TOKEN_FILE",
                        tokenFile.toString(),
                        "AWS_ROLE_ARN",
                        "arn:aws:iam::123456789:role/WebRole",
                        "AWS_ROLE_SESSION_NAME",
                        "test-session")::get)
                .build();
        var provider = new EnvWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(1, setup.resolvers().size());
    }

    @Test
    void skipsWhenTokenFileMissing() {
        var setup = ChainSetup.builder()
                .env(Map.of("AWS_ROLE_ARN", "arn:aws:iam::123456789:role/WebRole")::get)
                .build();
        var provider = new EnvWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(0, setup.resolvers().size());
    }

    @Test
    void skipsWhenRoleArnMissing(@TempDir Path tmp) throws IOException {
        Path tokenFile = tmp.resolve("token");
        Files.writeString(tokenFile, "my-web-identity-token");

        var setup = ChainSetup.builder()
                .env(Map.of("AWS_WEB_IDENTITY_TOKEN_FILE", tokenFile.toString())::get)
                .build();
        var provider = new EnvWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(0, setup.resolvers().size());
    }

    @Test
    void skipsForNonAwsIdentity(@TempDir Path tmp) throws IOException {
        Path tokenFile = tmp.resolve("token");
        Files.writeString(tokenFile, "my-web-identity-token");

        var setup = ChainSetup.builder()
                .env(Map.of(
                        "AWS_WEB_IDENTITY_TOKEN_FILE",
                        tokenFile.toString(),
                        "AWS_ROLE_ARN",
                        "arn:aws:iam::123456789:role/WebRole")::get)
                .build();
        var provider = new EnvWebIdentityProvider();
        setup.setCurrentProvider(provider);

        provider.setup(software.amazon.smithy.java.auth.api.identity.Identity.class, setup);
        assertEquals(0, setup.resolvers().size());
    }
}
