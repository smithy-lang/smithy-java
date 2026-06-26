/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.config.SharedConfigProvider;

/**
 * Verifies that an already-parsed {@link AwsProfileFile} supplied on the {@link ChainSetup} is used as-is and that
 * {@link SharedConfigProvider} does not overwrite it by reading from disk.
 */
class InjectedProfileFileTest {

    @Test
    void sharedConfigProviderUsesInjectedProfileFile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                aws_access_key_id = injected_ak
                aws_secret_access_key = injected_sk
                """);
        var injected = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();

        // Empty environment so the active profile resolves to "default" deterministically, regardless of any
        // AWS_PROFILE set in the ambient test environment.
        var setup = ChainSetup.builder().profileFile(injected).env(k -> null).build();
        var provider = new SharedConfigProvider();
        setup.setCurrentProvider(provider);
        provider.setup(software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity.class, setup);

        // The injected instance survives: SharedConfigProvider did not replace it with a disk load.
        assertSame(injected,
                setup.profileFile(),
                "SharedConfigProvider should defer to the profile file already set on the chain setup.");
        // And it resolved the active profile from that injected file.
        assertNotNull(setup.profile());
    }
}
