/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AwsProfileFileTest {

    @Test
    void mergesConfigAndCredentialsWithCredentialsTakingPrecedence(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Path creds = tmp.resolve("credentials");

        Files.writeString(config, """
                [default]
                region = us-east-1
                aws_access_key_id = CONFIG_KEY
                aws_secret_access_key = CONFIG_SECRET

                [profile dev]
                region = us-west-2
                """, StandardCharsets.UTF_8);

        Files.writeString(creds, """
                [default]
                aws_access_key_id = CREDS_KEY
                aws_secret_access_key = CREDS_SECRET
                aws_session_token = CREDS_TOKEN

                [dev]
                aws_access_key_id = DEV_KEY
                aws_secret_access_key = DEV_SECRET
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(creds)
                .build();

        assertEquals(2, file.profiles().size());

        AwsProfile def = file.profile("default");
        assertNotNull(def);
        assertEquals("us-east-1", def.property("region"));
        assertEquals("CREDS_KEY", def.property("aws_access_key_id"));
        assertEquals("CREDS_SECRET", def.property("aws_secret_access_key"));
        assertEquals("CREDS_TOKEN", def.property("aws_session_token"));

        AwsProfile dev = file.profile("dev");
        assertNotNull(dev);
        assertEquals("us-west-2", dev.property("region"));
        assertEquals("DEV_KEY", dev.property("aws_access_key_id"));
    }

    @Test
    void missingFilesAreTreatedAsEmpty(@TempDir Path tmp) {
        Path config = tmp.resolve("does-not-exist-config");
        Path creds = tmp.resolve("does-not-exist-credentials");

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(creds)
                .build();

        assertTrue(file.profiles().isEmpty());
        assertNull(file.profile("default"));
    }

    @Test
    void refreshMutatesInPlace(@TempDir Path tmp) throws IOException {
        Path creds = tmp.resolve("credentials");
        Files.writeString(creds, """
                [default]
                aws_access_key_id = V1
                aws_secret_access_key = V1_SECRET
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(null)
                .credentialsFile(creds)
                .build();

        // First snapshot.
        AwsProfile before = file.profile("default");
        assertEquals("V1", before.property("aws_access_key_id"));

        Files.writeString(creds, """
                [default]
                aws_access_key_id = V2
                aws_secret_access_key = V2_SECRET
                """, StandardCharsets.UTF_8);

        file.refresh();

        // After refresh, the file yields a new snapshot.
        AwsProfile after = file.profile("default");
        assertEquals("V2", after.property("aws_access_key_id"));
        // The previously-returned AwsProfile is immutable and unaffected.
        assertEquals("V1", before.property("aws_access_key_id"));
    }

    @Test
    void profilesListReturnedInFileOrder(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Path creds = tmp.resolve("credentials");
        Files.writeString(config, """
                [default]
                region = us-east-1

                [profile beta]
                region = us-east-2
                """, StandardCharsets.UTF_8);
        Files.writeString(creds, """
                [alpha]
                aws_access_key_id = A
                aws_secret_access_key = AS

                [beta]
                aws_access_key_id = B
                aws_secret_access_key = BS
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(creds)
                .build();

        List<String> names = new ArrayList<>();
        for (AwsProfile p : file.profiles()) {
            names.add(p.name());
        }
        assertEquals(List.of("default", "beta", "alpha"), names);
        assertEquals(List.of("default", "beta", "alpha"), file.profileNames());
    }

    @Test
    void subPropertiesFromConfigFileSurvive(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [profile ddb]
                region = us-west-2
                dynamodb =
                  endpoint_url = https://localhost:8000
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(null)
                .build();

        AwsProfile ddb = file.profile("ddb");
        assertNotNull(ddb);
        assertEquals("us-west-2", ddb.property("region"));
        assertEquals("https://localhost:8000", ddb.subProperties("dynamodb").get("endpoint_url"));
    }

    @Test
    void propertyKeysAreCaseInsensitive(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                REGION = us-east-1
                """, StandardCharsets.UTF_8);
        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(null)
                .build();
        AwsProfile def = file.profile("default");
        assertEquals("us-east-1", def.property("region"));
        assertEquals("us-east-1", def.property("Region"));
    }

    @Test
    void parseErrorsIncludeFilePathAndLineNumber(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, "[profile dev\nregion = us-west-2\n", StandardCharsets.UTF_8);

        ConfigFileParseException e = assertThrows(ConfigFileParseException.class,
                () -> AwsProfileFile.builder()
                        .configFile(config)
                        .credentialsFile(null)
                        .build());
        assertEquals(1, e.lineNumber());
        assertTrue(e.getMessage().contains(config.toString()));
    }

    @Test
    void ssoSessionsExposedFromConfigFile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                region = us-east-1

                [sso-session corp]
                sso_region = us-west-2
                sso_start_url = https://corp.awsapps.com/start
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(config)
                .credentialsFile(null)
                .build();

        assertNotNull(file.ssoSessions());
        assertEquals(1, file.ssoSessions().size());
        AwsProfile session = file.ssoSessions().get("corp");
        assertNotNull(session);
        assertEquals("us-west-2", session.property("sso_region"));
        assertEquals("https://corp.awsapps.com/start", session.property("sso_start_url"));
    }

    @Test
    void onlyCredentialsFileWorks(@TempDir Path tmp) throws IOException {
        Path creds = tmp.resolve("credentials");
        Files.writeString(creds, """
                [default]
                aws_access_key_id = ONLY
                aws_secret_access_key = ONLY_SECRET
                """, StandardCharsets.UTF_8);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(null)
                .credentialsFile(creds)
                .build();

        assertEquals(1, file.profiles().size());
        assertNotNull(file.profile("default"));
        assertNull(file.profile("missing"));
        assertFalse(file.profiles().isEmpty());
    }
}
