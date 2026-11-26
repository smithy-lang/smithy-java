/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.server.proxy.ProxyService;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

class PromptLoaderTest {

    @Test
    public void testLoadPromptsWithNoServices() {
        var prompts = PromptLoader.loadPrompts(Collections.emptyList());
        assertTrue(prompts.isEmpty());
    }

    @Test
    public void testLoadPromptsWithUniqueNormalizedNames() {
        var model = Model.assembler()
                .addUnparsedModel("test-unique.smithy", UNIQUE_PROMPTS_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var service = ProxyService.builder()
                .service(ShapeId.from("smithy.test.unique#TestService"))
                .proxyEndpoint("http://localhost")
                .model(model)
                .build();

        var prompts = PromptLoader.loadPrompts(List.of(service));

        // Should have 3 prompts with unique normalized names
        assertEquals(3, prompts.size());
        assertTrue(prompts.containsKey("getuser"));
        assertTrue(prompts.containsKey("createuser"));
        assertTrue(prompts.containsKey("deleteuser"));
    }

    @Test
    public void testLoadPromptsWithDuplicateNormalizedNamesFromDifferentServices() {
        var model1 = Model.assembler()
                .addUnparsedModel("test-service1.smithy", SERVICE1_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var model2 = Model.assembler()
                .addUnparsedModel("test-service2.smithy", SERVICE2_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var service1 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.service1#TestService1"))
                .proxyEndpoint("http://localhost")
                .model(model1)
                .build();

        var service2 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.service2#TestService2"))
                .proxyEndpoint("http://localhost")
                .model(model2)
                .build();

        var exception = assertThrows(RuntimeException.class, () -> {
            PromptLoader.loadPrompts(List.of(service1, service2));
        });

        String message = exception.getMessage();
        assertTrue(message.contains("Duplicate normalized prompt name 'getuser' found"));
        assertTrue(message.contains("Original name 'getuser' conflicts with previously registered name 'GetUser'"));
    }

    @Test
    public void testLoadPromptsWithCaseSensitivityVariationsAcrossServices() {
        var model1 = Model.assembler()
                .addUnparsedModel("test-case1.smithy", CASE_SERVICE1_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var model2 = Model.assembler()
                .addUnparsedModel("test-case2.smithy", CASE_SERVICE2_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var service1 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.case1#TestService1"))
                .proxyEndpoint("http://localhost")
                .model(model1)
                .build();

        var service2 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.case2#TestService2"))
                .proxyEndpoint("http://localhost")
                .model(model2)
                .build();

        var exception = assertThrows(RuntimeException.class, () -> {
            PromptLoader.loadPrompts(List.of(service1, service2));
        });

        String message = exception.getMessage();
        assertTrue(message.contains("Duplicate normalized prompt name 'getuser' found"));
        assertTrue(message.contains("Original name 'GETUSER' conflicts with previously registered name 'GetUser'"));
    }

    @Test
    public void testLoadPromptsWithSingleCharacterNamesAcrossServices() {
        var model1 = Model.assembler()
                .addUnparsedModel("test-single1.smithy", SINGLE_SERVICE1_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var model2 = Model.assembler()
                .addUnparsedModel("test-single2.smithy", SINGLE_SERVICE2_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();

        var service1 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.single1#TestService1"))
                .proxyEndpoint("http://localhost")
                .model(model1)
                .build();

        var service2 = ProxyService.builder()
                .service(ShapeId.from("smithy.test.single2#TestService2"))
                .proxyEndpoint("http://localhost")
                .model(model2)
                .build();

        var exception = assertThrows(RuntimeException.class, () -> {
            PromptLoader.loadPrompts(List.of(service1, service2));
        });

        String message = exception.getMessage();
        assertTrue(message.contains("Duplicate normalized prompt name 'a' found"));
        assertTrue(message.contains("Original name 'a' conflicts with previously registered name 'A'"));
    }

    @Test
    public void testNormalizeMethod() {
        // Test the normalize method directly
        assertEquals("getuser", PromptLoader.normalize("GetUser"));
        assertEquals("getuser", PromptLoader.normalize("GETUSER"));
        assertEquals("getuser", PromptLoader.normalize("getuser"));
        assertEquals("getuser", PromptLoader.normalize("getUser"));
        assertEquals("a", PromptLoader.normalize("A"));
        assertEquals("a", PromptLoader.normalize("a"));
        assertEquals("get-user", PromptLoader.normalize("Get-User"));
        assertEquals("get_user", PromptLoader.normalize("Get_User"));
    }

    // Test model with unique normalized prompt names
    private static final String UNIQUE_PROMPTS_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.unique

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        GetUser: { description: "Get a user", template: "Get user information" },
                        CreateUser: { description: "Create a user", template: "Create new user" },
                        DeleteUser: { description: "Delete a user", template: "Delete existing user" }
                    })
                    service TestService {
                        operations: []
                    }
                    """;

    // First service with GetUser prompt
    private static final String SERVICE1_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.service1

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        GetUser: { description: "Get a user", template: "Get user information" }
                    })
                    service TestService1 {
                        operations: []
                    }
                    """;

    // Second service with getuser prompt (different case, same normalized name)
    private static final String SERVICE2_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.service2

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        getuser: { description: "get a user", template: "get user information" }
                    })
                    service TestService2 {
                        operations: []
                    }
                    """;

    // First service with GetUser prompt for case sensitivity test
    private static final String CASE_SERVICE1_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.case1

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        GetUser: { description: "Get a user", template: "Get user information" }
                    })
                    service TestService1 {
                        operations: []
                    }
                    """;

    // Second service with GETUSER prompt for case sensitivity test
    private static final String CASE_SERVICE2_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.case2

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        GETUSER: { description: "GET A USER", template: "GET USER INFORMATION" }
                    })
                    service TestService2 {
                        operations: []
                    }
                    """;

    // First service with A prompt for single character test
    private static final String SINGLE_SERVICE1_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.single1

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        A: { description: "Prompt A", template: "Template A" }
                    })
                    service TestService1 {
                        operations: []
                    }
                    """;

    // Second service with a prompt for single character test
    private static final String SINGLE_SERVICE2_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.single2

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        a: { description: "prompt a", template: "template a" }
                    })
                    service TestService2 {
                        operations: []
                    }
                    """;

    // Test model with special characters in prompt names
    private static final String SPECIAL_CHARS_MODEL =
            """
                    $version: "2"

                    namespace smithy.test.special

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        "Get-User": { description: "Get a user", template: "Get user information" },
                        "Get_Item": { description: "Get an item", template: "Get item information" }
                    })
                    service TestService {
                        operations: []
                    }
                    """;
}
