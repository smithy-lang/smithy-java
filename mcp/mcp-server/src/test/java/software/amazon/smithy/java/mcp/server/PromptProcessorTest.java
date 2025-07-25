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

public class PromptProcessorTest {

    private final PromptProcessor processor = new PromptProcessor();

    @Test
    public void testApplyTemplateArgumentsWithSimpleSubstitution() {
        String template = "Hello {{name}}!";
        Document arguments = Document.of(Map.of("name", Document.of("World")));

        String result = processor.applyTemplateArguments(template, arguments);

        assertEquals("Hello World!", result);
    }

    @Test
    public void testApplyTemplateArgumentsWithMultipleSubstitutions() {
        String template = "{{greeting}} {{name}}, welcome to {{place}}!";
        Document arguments = Document.of(Map.of(
                "greeting",
                Document.of("Hello"),
                "name",
                Document.of("X"),
                "place",
                Document.of("P")));

        String result = processor.applyTemplateArguments(template, arguments);

        assertEquals("Hello X, welcome to P!", result);
    }

    @Test
    public void testApplyTemplateArgumentsWithMissingArgument() {
        String template = "Hello {{name}}!";
        Document arguments = Document.of(Map.of("other", Document.of("value")));

        String result = processor.applyTemplateArguments(template, arguments);

        assertEquals("Hello !", result);
    }

    @Test
    public void testApplyTemplateArgumentsWithNoPlaceholders() {
        String template = "Hello World!";
        Document arguments = Document.of(Map.of("name", Document.of("John")));

        String result = processor.applyTemplateArguments(template, arguments);

        assertEquals("Hello World!", result);
    }

    @Test
    public void testApplyTemplateArgumentsWithNullArguments() {
        String template = "Hello {{name}}!";

        String result = processor.applyTemplateArguments(template, null);

        assertEquals("Hello {{name}}!", result);
    }

    @Test
    public void testBuildPromptResultWithValidTemplate() {
        PromptInfo prompt = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .template("Hello {{name}}!")
                .arguments(List.of())
                .build();

        Document arguments = Document.of(Map.of("name", Document.of("World")));

        var result = processor.buildPromptResult(prompt, arguments);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Hello World!", result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testBuildPromptResultWithNullTemplate() {
        PromptInfo prompt = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .template(null)
                .arguments(List.of())
                .build();

        var result = processor.buildPromptResult(prompt, null);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Template is required for the prompt:test-prompt",
                result.getMessages().get(0).getContent().getText());
    }

    @Test
    public void testBuildPromptResultWithMissingRequiredArguments() {
        PromptArgument requiredArg = PromptArgument.builder()
                .name("name")
                .description("The name")
                .required(true)
                .build();

        PromptInfo prompt = PromptInfo.builder()
                .name("test-prompt")
                .description("A test prompt")
                .template("Hello {{name}}!")
                .arguments(List.of(requiredArg))
                .build();

        var result = processor.buildPromptResult(prompt, null);

        assertNotNull(result);
        assertEquals("A test prompt", result.getDescription());
        assertEquals(1, result.getMessages().size());
        assertEquals("Tell user that there are missing arguments for the prompt : [name]",
                result.getMessages().get(0).getContent().getText());
    }
}
