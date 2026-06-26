/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.config.CredentialProcessHandler;

class CredentialProcessTerminalTest {
    @Test
    void claimsTerminalWhenProfileDeclaresCredentialProcess(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, "[default]\ncredential_process = /usr/local/bin/awscreds\n");
        var file = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();

        var setup = ChainSetup.builder().build();
        setup.setProfileFile(file);
        setup.setProfile(file.activeProfile(k -> null));

        var handler = new CredentialProcessHandler();
        setup.setCurrentProvider(handler);
        handler.setup(AwsCredentialsIdentity.class, setup);

        assertTrue(setup.isTerminal(),
                "A profile declaring credential_process must claim the chain terminally so a process failure "
                        + "stops resolution instead of falling through to a lower-priority provider.");
    }
}
