/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileStandardizerTest {

    @Test
    void lowerCasesKeysAndPreservesValues() {
        String content = """
                [default]
                REGION = us-east-1
                AWS_Access_Key_Id = AKIA
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals("us-east-1", profiles.get("default").property("region"));
        assertEquals("us-east-1", profiles.get("default").property("Region"));
        assertEquals("AKIA", profiles.get("default").property("aws_access_key_id"));
    }

    @Test
    void configFileDropsNonDefaultSectionsWithoutProfilePrefix() {
        String content = """
                [default]
                region = us-east-1

                [foo]
                region = us-west-2
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CONFIGURATION);
        assertEquals(List.of("default"), List.copyOf(profiles.keySet()));
    }

    @Test
    void configFileAcceptsProfilePrefixedSections() {
        String content = """
                [default]
                region = us-east-1

                [profile foo]
                region = us-west-2
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CONFIGURATION);
        assertEquals(List.of("default", "foo"), List.copyOf(profiles.keySet()));
    }

    @Test
    void credentialsFileRejectsProfilePrefix() {
        String content = """
                [default]
                aws_access_key_id = A

                [profile foo]
                aws_access_key_id = B
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals(List.of("default"), List.copyOf(profiles.keySet()));
    }

    @Test
    void profileDefaultSupersedesDefaultInConfigFile() {
        String content = """
                [default]
                aws_access_key_id = A
                aws_secret_access_key = S
                region = us-west-1

                [profile default]
                aws_access_key_id = B

                [profile default]
                aws_secret_access_key = T

                [default]
                region = us-west-1
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CONFIGURATION);
        AwsProfile d = profiles.get("default");
        assertEquals("B", d.property("aws_access_key_id"));
        assertEquals("T", d.property("aws_secret_access_key"));
        assertNull(d.property("region"));
    }

    @Test
    void duplicateProfilesInSameFileAreMerged() {
        String content = """
                [default]
                aws_access_key_id = A

                [default]
                aws_secret_access_key = S
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals("A", profiles.get("default").property("aws_access_key_id"));
        assertEquals("S", profiles.get("default").property("aws_secret_access_key"));
    }

    @Test
    void invalidProfileNameIsSilentlyDropped() {
        String content = """
                [default]
                aws_access_key_id = A

                [not valid]
                aws_access_key_id = IGNORED
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals(List.of("default"), List.copyOf(profiles.keySet()));
    }

    @Test
    void invalidPropertyNameIsSilentlyDropped() {
        String content = """
                [default]
                region = us-east-1
                bad key = dropped
                aws_access_key_id = A
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        AwsProfile d = profiles.get("default");
        assertEquals("us-east-1", d.property("region"));
        assertEquals("A", d.property("aws_access_key_id"));
        assertNull(d.property("bad key"));
    }

    @Test
    void ssoSessionAndServicesOnlyInConfigFile() {
        String content = """
                [default]
                region = us-east-1

                [sso-session my-session]
                sso_start_url = https://example.awsapps.com/start

                [services my-services]
                dynamodb =
                  endpoint_url = https://localhost:8000
                """;
        Map<String, AwsProfile> configProfiles = standardize(content, AwsConfigFileType.CONFIGURATION);
        assertEquals(List.of("default"), List.copyOf(configProfiles.keySet()));
        Map<String, AwsProfile> credProfiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals(List.of("default"), List.copyOf(credProfiles.keySet()));
    }

    @Test
    void subPropertiesExposedOnAwsProfile() {
        String content = """
                [default]
                s3 =
                  max_concurrent_requests = 30
                  max_retries = 10
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CONFIGURATION);
        AwsProfile d = profiles.get("default");
        Map<String, String> subs = d.subProperties("s3");
        assertNotNull(subs);
        assertEquals(Map.of("max_concurrent_requests", "30", "max_retries", "10"), subs);
        // Case-insensitive lookup works for sub-property parents too.
        assertNotNull(d.subProperties("S3"));
    }

    @Test
    void emptySectionNameIsDropped() {
        String content = """
                []
                region = ignored
                [default]
                region = us-east-1
                """;
        Map<String, AwsProfile> profiles = standardize(content, AwsConfigFileType.CREDENTIALS);
        assertEquals(List.of("default"), List.copyOf(profiles.keySet()));
    }

    private static Map<String, AwsProfile> standardize(String content, AwsConfigFileType fileType) {
        return ProfileStandardizer.standardize(AwsProfileFileParser.parse(content), fileType).profiles();
    }
}
