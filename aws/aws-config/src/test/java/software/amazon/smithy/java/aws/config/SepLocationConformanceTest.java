/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the reference location test suite for AWS shared configuration file path resolution.
 */
class SepLocationConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> locationTests() throws IOException {
        JsonNode root;
        try (InputStream is = getClass().getResourceAsStream("config-file-location-tests.json")) {
            assertNotNull(is, "config-file-location-tests.json not found on classpath");
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

    private void runTest(JsonNode test) {
        Map<String, String> env = new HashMap<>();
        if (test.has("environment")) {
            Iterator<Map.Entry<String, JsonNode>> fields = test.get("environment").properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                env.put(entry.getKey(), entry.getValue().asText());
            }
        }

        String platform = test.has("platform") ? test.get("platform").asText() : null;
        String languageSpecificHome = test.has("languageSpecificHome")
                ? test.get("languageSpecificHome").asText()
                : null;

        // Build property getter that simulates os.name and user.home.
        Function<String, String> propertyGetter = key -> {
            if ("os.name".equals(key)) {
                if ("windows".equals(platform)) {
                    return "Windows 10";
                } else if ("linux".equals(platform)) {
                    return "Linux";
                }
                return null;
            }
            if ("user.home".equals(key)) {
                return "ignored".equals(languageSpecificHome) ? null : languageSpecificHome;
            }
            return null;
        };

        Function<String, String> envGetter = key -> {
            String val = env.get(key);
            return "ignored".equals(val) ? null : val;
        };

        String expectedConfig = test.get("configLocation").asText();
        String expectedCreds = test.get("credentialsLocation").asText();

        AwsProfileFile.ResolvedPaths paths = AwsProfileFile.resolveDefaultPaths(envGetter, propertyGetter);

        // Normalize separators for cross-platform comparison.
        assertEquals(normalize(expectedConfig),
                normalize(paths.configLocation().toString()),
                "Config location mismatch");
        assertEquals(normalize(expectedCreds),
                normalize(paths.credentialsLocation().toString()),
                "Credentials location mismatch");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
