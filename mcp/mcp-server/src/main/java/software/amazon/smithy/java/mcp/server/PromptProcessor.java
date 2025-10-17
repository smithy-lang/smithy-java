/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.GetPromptResult;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.PromptMessage;
import software.amazon.smithy.java.mcp.model.PromptMessageContent;
import software.amazon.smithy.java.mcp.model.PromptMessageContentType;
import software.amazon.smithy.java.mcp.model.PromptRole;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Handles processing of prompt templates and building prompt results.
 */
@SmithyUnstableApi
public final class PromptProcessor {

    private static final Pattern PROMPT_ARGUMENT_PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * Builds a GetPromptResult from a PromptInfo and provided arguments.
     *
     * @param prompt The prompt information containing template and metadata
     * @param arguments Document containing argument values for template substitution
     * @return GetPromptResult with processed template or error messages
     */
    public GetPromptResult buildPromptResult(Prompt prompt, Document arguments) {
        String template = prompt.promptTemplate();
        if (template == null) {
            return GetPromptResult.builder()
                    .description(prompt.promptInfo().getDescription())
                    .messages(List.of(
                            PromptMessage.builder()
                                    .role(PromptRole.ASSISTANT.getValue())
                                    .content(PromptMessageContent.builder()
                                            .type(PromptMessageContentType.TEXT)
                                            .text("Template is required for the prompt:"
                                                    + prompt.promptInfo().getName())
                                            .build())
                                    .build()))
                    .build();
        }

        var requiredArguments = getRequiredArguments(prompt.promptInfo());

        if (!requiredArguments.isEmpty() && arguments == null) {
            return GetPromptResult.builder()
                    .description(prompt.promptInfo().getDescription())
                    .messages(List.of(PromptMessage.builder()
                            .role(PromptRole.USER.getValue())
                            .content(PromptMessageContent.builder()
                                    .type(PromptMessageContentType.TEXT)
                                    .text("Tell user that there are missing arguments for the prompt : "
                                            + requiredArguments)
                                    .build())
                            .build()))
                    .build();
        }

        String processedText = applyTemplateArguments(template, arguments);

        return GetPromptResult.builder()
                .description(prompt.promptInfo().getDescription())
                .messages(List.of(
                        PromptMessage.builder()
                                .role(PromptRole.USER.getValue())
                                .content(PromptMessageContent.builder()
                                        .type(PromptMessageContentType.TEXT)
                                        .text(processedText)
                                        .build())
                                .build()))
                .build();
    }

    /**
     * Applies template arguments to a template string.
     *
     * //TODO: Optimize it with indexes where the replacements need to be done.
     * @param template The template string containing {{placeholder}} patterns
     * @param arguments Document containing replacement values
     * @return The template with all placeholders replaced
     */
    public String applyTemplateArguments(String template, Document arguments) {
        // Common cases
        if (template == null || arguments == null || template.isEmpty()) {
            return template;
        }

        // Avoid any regex work if there are no potential placeholders
        int firstBrace = template.indexOf("{{");
        if (firstBrace == -1) {
            return template;
        }

        Matcher matcher = PROMPT_ARGUMENT_PLACEHOLDER.matcher(template);

        int matchCount = 0;
        int estimatedResultLength = template.length();
        Map<String, String> replacementCache = new HashMap<>();

        while (matcher.find()) {
            matchCount++;
            String argName = matcher.group(1);

            // Only look up each unique argument once
            if (!replacementCache.containsKey(argName)) {
                Document argValue = arguments.getMember(argName);
                String replacement = (argValue != null) ? argValue.asString() : "";
                replacementCache.put(argName, replacement);

                // Adjust estimated length (subtract placeholder length, add replacement length)
                estimatedResultLength = estimatedResultLength - matcher.group(0).length() + replacement.length();
            }
        }

        // If no matches found, return original template
        if (matchCount == 0) {
            return template;
        }

        // Reset matcher for the actual replacement pass
        matcher.reset();

        StringBuilder result = new StringBuilder(estimatedResultLength);

        // Single-pass replacement using cached values
        while (matcher.find()) {
            String argName = matcher.group(1);
            String replacement = replacementCache.get(argName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Extracts the set of required argument names from a PromptInfo.
     *
     * @param promptInfo The prompt information to analyze
     * @return Set of required argument names
     */
    private Set<String> getRequiredArguments(PromptInfo promptInfo) {
        return promptInfo.getArguments()
                .stream()
                .filter(PromptArgument::isRequired)
                .map(PromptArgument::getName)
                .collect(Collectors.toSet());
    }
}
