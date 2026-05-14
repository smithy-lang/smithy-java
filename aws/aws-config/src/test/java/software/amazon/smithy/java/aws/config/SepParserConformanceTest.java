/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the reference test suite from config-file-parser-tests.json.
 */
class SepParserConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tmp;

    @TestFactory
    Stream<DynamicTest> conformanceTests() throws IOException {
        JsonNode root;
        try (InputStream is = getClass().getResourceAsStream("config-file-parser-tests.json")) {
            assertNotNull(is, "config-file-parser-tests.json not found on classpath");
            root = MAPPER.readTree(is);
        }

        JsonNode tests = root.get("tests");
        assertNotNull(tests, "No 'tests' array in JSON");

        List<DynamicTest> dynamicTests = new ArrayList<>();
        for (JsonNode test : tests) {
            String name = test.has("name") ? test.get("name").asText() : "unnamed";
            dynamicTests.add(DynamicTest.dynamicTest(name, () -> runTest(test)));
        }
        return dynamicTests.stream();
    }

    private void runTest(JsonNode test) throws IOException {
        JsonNode input = test.get("input");
        JsonNode output = test.get("output");

        String configContent = input.has("configFile") ? input.get("configFile").asText() : null;
        String credentialsContent = input.has("credentialsFile") ? input.get("credentialsFile").asText() : null;

        Path configPath = null;
        Path credentialsPath = null;
        if (configContent != null) {
            configPath = tmp.resolve("config-" + System.nanoTime());
            Files.writeString(configPath, configContent, StandardCharsets.UTF_8);
        }
        if (credentialsContent != null) {
            credentialsPath = tmp.resolve("credentials-" + System.nanoTime());
            Files.writeString(credentialsPath, credentialsContent, StandardCharsets.UTF_8);
        }

        if (output.has("errorContaining")) {
            String expectedError = output.get("errorContaining").asText();
            try {
                buildFile(configPath, credentialsPath);
                fail("Expected an error containing: " + expectedError);
            } catch (ConfigFileParseException e) {
                assertTrue(e.getMessage().toLowerCase().contains(expectedError.toLowerCase()),
                        "Error message '" + e.getMessage() + "' does not contain '" + expectedError + "'");
            }
            return;
        }

        AwsProfileFile file = buildFile(configPath, credentialsPath);

        if (output.has("profiles")) {
            Map<String, Map<String, Object>> expectedProfiles = parseExpectedProfiles(output.get("profiles"));
            assertProfilesMatch(expectedProfiles, file);
        }

        if (output.has("ssoSessions")) {
            Map<String, Map<String, Object>> expectedSessions = parseExpectedProfiles(output.get("ssoSessions"));
            assertSsoSessionsMatch(expectedSessions, file);
        }
    }

    private AwsProfileFile buildFile(Path configPath, Path credentialsPath) {
        AwsProfileFile.Builder builder = AwsProfileFile.builder();
        if (configPath != null) {
            builder.configFile(configPath);
        } else {
            builder.configFile(null);
        }
        if (credentialsPath != null) {
            builder.credentialsFile(credentialsPath);
        } else {
            builder.credentialsFile(null);
        }
        return builder.build();
    }

    private Map<String, Map<String, Object>> parseExpectedProfiles(JsonNode profilesNode) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = profilesNode.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String profileName = entry.getKey();
            JsonNode propsNode = entry.getValue();
            Map<String, Object> props = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> propFields = propsNode.properties().iterator();
            while (propFields.hasNext()) {
                Map.Entry<String, JsonNode> propEntry = propFields.next();
                String key = propEntry.getKey();
                JsonNode val = propEntry.getValue();
                if (val.isObject()) {
                    // Sub-property
                    Map<String, String> subProps = new LinkedHashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> subFields = val.properties().iterator();
                    while (subFields.hasNext()) {
                        Map.Entry<String, JsonNode> subEntry = subFields.next();
                        subProps.put(subEntry.getKey(), subEntry.getValue().asText());
                    }
                    props.put(key, subProps);
                } else {
                    props.put(key, val.asText());
                }
            }
            result.put(profileName, props);
        }
        return result;
    }

    private void assertProfilesMatch(Map<String, Map<String, Object>> expected, AwsProfileFile file) {
        // Check profile names match.
        assertEquals(expected.keySet(),
                profileNameSet(file),
                "Profile names mismatch");

        for (Map.Entry<String, Map<String, Object>> e : expected.entrySet()) {
            String name = e.getKey();
            AwsProfile profile = file.profile(name);
            assertNotNull(profile, "Profile '" + name + "' not found");
            Map<String, Object> expectedProps = e.getValue();
            for (Map.Entry<String, Object> pe : expectedProps.entrySet()) {
                String key = pe.getKey();
                Object expectedValue = pe.getValue();
                if (expectedValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> expectedSubs = (Map<String, String>) expectedValue;
                    Map<String, String> actualSubs = profile.subProperties(key);
                    assertNotNull(actualSubs, "Sub-properties for '" + key + "' not found in profile '" + name + "'");
                    assertEquals(expectedSubs,
                            actualSubs,
                            "Sub-properties mismatch for '" + key + "' in profile '" + name + "'");
                } else {
                    String actual = profile.property(key);
                    assertEquals((String) expectedValue,
                            actual,
                            "Property '" + key + "' mismatch in profile '" + name + "'");
                }
            }
            // Verify no extra properties.
            assertEquals(expectedProps.size(),
                    countProperties(profile, expectedProps),
                    "Extra properties in profile '" + name + "'");
        }
    }

    private void assertSsoSessionsMatch(Map<String, Map<String, Object>> expected, AwsProfileFile file) {
        Map<String, AwsProfile> actualSessions = file.ssoSessions();
        assertEquals(expected.keySet(), actualSessions.keySet(), "SSO session names mismatch");
        for (Map.Entry<String, Map<String, Object>> e : expected.entrySet()) {
            String name = e.getKey();
            AwsProfile actual = actualSessions.get(name);
            assertNotNull(actual, "SSO session '" + name + "' not found");
            Map<String, Object> expectedProps = e.getValue();
            for (Map.Entry<String, Object> pe : expectedProps.entrySet()) {
                String key = pe.getKey();
                Object expectedValue = pe.getValue();
                if (expectedValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> expectedSubs = (Map<String, String>) expectedValue;
                    Map<String, String> actualSubs = actual.subProperties(key);
                    assertNotNull(actualSubs,
                            "SSO session '" + name + "' sub-properties for '" + key + "' not found");
                    assertEquals(expectedSubs,
                            actualSubs,
                            "SSO session '" + name + "' property '" + key + "' mismatch");
                } else {
                    assertEquals(expectedValue,
                            actual.property(key),
                            "SSO session '" + name + "' property '" + key + "' mismatch");
                }
            }
        }
    }

    private static Set<String> profileNameSet(AwsProfileFile file) {
        Set<String> names = new LinkedHashSet<>();
        for (AwsProfile p : file.profiles()) {
            names.add(p.name());
        }
        return names;
    }

    private static int countProperties(AwsProfile profile, Map<String, Object> expected) {
        int count = 0;
        for (Map.Entry<String, String> e : profile.properties().entrySet()) {
            if (expected.containsKey(e.getKey())) {
                count++;
            }
        }
        for (Map.Entry<String, Map<String, String>> e : profile.subProperties().entrySet()) {
            if (expected.containsKey(e.getKey())) {
                count++;
            }
        }
        return count;
    }
}
