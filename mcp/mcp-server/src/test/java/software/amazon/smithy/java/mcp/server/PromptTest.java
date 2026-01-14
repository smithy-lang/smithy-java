/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;

public class PromptTest {

    @Test
    public void testGetPromptResultWithSimpleSubstitution() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello {{name}}!");
        Document arguments = Document.of(Map.of("name", Document.of("World")));

        var result = prompt.getPromptResult(arguments, null);

        assertNotNull(result);
        assertEquals("Hello World!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithMultipleSubstitutions() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "{{greeting}} {{name}}, welcome to {{place}}!");
        Document arguments = Document.of(Map.of(
                "greeting",
                Document.of("Hello"),
                "name",
                Document.of("X"),
                "place",
                Document.of("P")));

        var result = prompt.getPromptResult(arguments, null);

        assertNotNull(result);
        assertEquals("Hello X, welcome to P!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithMissingArgument() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello {{name}}!");
        Document arguments = Document.of(Map.of("other", Document.of("value")));

        var result = prompt.getPromptResult(arguments, null);

        assertNotNull(result);
        assertEquals("Hello !", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithNoPlaceholders() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello World!");
        Document arguments = Document.of(Map.of("name", Document.of("John")));

        var result = prompt.getPromptResult(arguments, null);

        assertNotNull(result);
        assertEquals("Hello World!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithNullArguments() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello {{name}}!");

        var result = prompt.getPromptResult(null, null);

        assertNotNull(result);
        // When arguments is null and there are no required arguments,
        // template placeholders remain unchanged
        assertEquals("Hello {{name}}!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithValidTemplate() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello {{name}}!");

        Document arguments = Document.of(Map.of("name", Document.of("World")));

        var result = prompt.getPromptResult(arguments, null);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Hello World!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithNullTemplate() {
        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of())
                .build();

        Prompt prompt = new Prompt(promptInfo, (String) null);

        var result = prompt.getPromptResult(null, null);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Template is required for the prompt:test-prompt",
                result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testGetPromptResultWithMissingRequiredArguments() {
        PromptArgument requiredArg = PromptArgument.builder()
                .name("name")
                .description("The name")
                .required(true)
                .build();

        PromptInfo promptInfo = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .arguments(List.of(requiredArg))
                .build();

        Prompt prompt = new Prompt(promptInfo, "Hello {{name}}!");

        var result = prompt.getPromptResult(null, null);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Tell user that there are missing arguments for the prompt : [name]",
                result.getMessages().get(0).getContent().getText());
    }
}
