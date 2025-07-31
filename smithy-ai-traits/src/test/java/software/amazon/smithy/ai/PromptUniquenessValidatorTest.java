/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.validation.ValidatedResult;
import software.amazon.smithy.model.validation.ValidationEvent;

public class PromptUniquenessValidatorTest {

    /**
     * Test case record for prompt uniqueness validation scenarios.
     * 
     * @param testName descriptive name for the test scenario
     * @param resourcePath path to the Smithy model file
     * @param expectedErrorCount number of validation errors expected
     * @param expectedErrorContent specific content to verify in error messages (null if no errors expected)
     */
    public record ValidationTestCase(
            String testName,
            String resourcePath,
            int expectedErrorCount,
            String expectedErrorContent) {
        @Override
        public String toString() {
            return testName;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidationTestCases")
    @DisplayName("Prompt uniqueness validation scenarios")
    public void testPromptUniquenessValidation(ValidationTestCase testCase) {
        var result = assembleModelFromResource(testCase.resourcePath());
        List<ValidationEvent> duplicateEvents = filterDuplicatePromptEvents(result.getValidationEvents());

        assertEquals(testCase.expectedErrorCount(),
                duplicateEvents.size(),
                String.format("Test '%s': Expected %d validation errors",
                        testCase.testName(),
                        testCase.expectedErrorCount()));

        if (testCase.expectedErrorCount() > 0) {
            ValidationEvent event = duplicateEvents.get(0);
            assertTrue(event.getMessage().contains("Duplicate prompt name detected"),
                    String.format("Test '%s': Should contain duplicate prompt error message", testCase.testName()));

            if (testCase.expectedErrorContent() != null) {
                assertTrue(event.getMessage().contains(testCase.expectedErrorContent()),
                        String.format("Test '%s': Should contain expected error content: %s",
                                testCase.testName(),
                                testCase.expectedErrorContent()));
            }

            assertTrue(event.getMessage().contains("case-insensitively"),
                    String.format("Test '%s': Should mention case-insensitive comparison", testCase.testName()));
        }
    }

    /**
     * Provides test cases for prompt uniqueness validation scenarios.
     * Each test case includes: test name, resource path, expected error count, and expected error content.
     */
    private static Stream<ValidationTestCase> provideValidationTestCases() {
        return Stream.of(
                new ValidationTestCase(
                        "No duplicate prompts",
                        "/unique-prompts.smithy",
                        0,
                        null),
                new ValidationTestCase(
                        "Duplicate prompts within service",
                        "/service-level-duplicates.smithy",
                        1,
                        "Search_Users"),
                new ValidationTestCase(
                        "Duplicate prompts between service and operation",
                        "/duplicate-prompts.smithy",
                        1,
                        "Search_Users"),
                new ValidationTestCase(
                        "Same prompt names across different services (should be allowed)",
                        "/cross-service-unique.smithy",
                        0,
                        null));
    }

    private ValidatedResult<Model> assembleModelFromResource(String resourcePath) {
        return Model.assembler()
                .addImport(Objects.requireNonNull(getClass().getResource(resourcePath)))
                .discoverModels(getClass().getClassLoader())
                .assemble();
    }

    private List<ValidationEvent> filterDuplicatePromptEvents(List<ValidationEvent> events) {
        return events.stream()
                .filter(event -> event.getMessage().contains("Duplicate prompt name detected"))
                .toList();
    }
}
