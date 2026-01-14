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
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.PromptMessage;
import software.amazon.smithy.java.mcp.model.PromptMessageContent;
import software.amazon.smithy.java.mcp.model.PromptMessageContentType;
import software.amazon.smithy.java.mcp.model.PromptRole;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Represents a prompt that can be either local (with a template) or proxied to a remote MCP server.
 */
@SmithyUnstableApi
public final class Prompt {

    private static final Pattern PROMPT_ARGUMENT_PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final PromptInfo promptInfo;
    private final String promptTemplate;
    private final McpServerProxy proxy;

    /**
     * Creates a local prompt with a template.
     *
     * @param promptInfo The prompt metadata
     * @param promptTemplate The template string containing {{placeholder}} patterns
     */
    public Prompt(PromptInfo promptInfo, String promptTemplate) {
        this.promptInfo = promptInfo;
        this.promptTemplate = promptTemplate;
        this.proxy = null;
    }

    /**
     * Creates a proxy prompt that delegates to a remote MCP server.
     *
     * @param promptInfo The prompt metadata
     * @param proxy The MCP server proxy to delegate to
     */
    public Prompt(PromptInfo promptInfo, McpServerProxy proxy) {
        this.promptInfo = promptInfo;
        this.promptTemplate = null;
        this.proxy = proxy;
    }

    /**
     * @return The prompt metadata
     */
    public PromptInfo promptInfo() {
        return promptInfo;
    }

    /**
     * Gets the prompt result, either by processing the local template or by
     * forwarding the request to the proxy server.
     *
     * @param arguments Document containing argument values for template substitution
     * @param requestId The request ID to use for proxy calls (may be null for local prompts)
     * @return GetPromptResult with processed template or proxy response
     */
    public GetPromptResult getPromptResult(Document arguments, Document requestId) {
        if (proxy != null) {
            return delegateToProxy(arguments, requestId);
        }
        return buildLocalPromptResult(arguments);
    }

    /**
     * Delegates the prompt request to the proxy server via RPC.
     */
    private GetPromptResult delegateToProxy(Document arguments, Document requestId) {
        Map<String, Document> params = new HashMap<>();
        params.put("name", Document.of(promptInfo.getName()));
        if (arguments != null) {
            params.put("arguments", arguments);
        }

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("prompts/get")
                .id(requestId)
                .params(Document.of(params))
                .jsonrpc("2.0")
                .build();

        return proxy.rpc(request).thenApply(response -> {
            if (response.getError() != null) {
                throw new RuntimeException("Error getting prompt: " + response.getError().getMessage());
            }
            return response.getResult().asShape(GetPromptResult.builder());
        }).join();
    }

    /**
     * Builds a GetPromptResult from the local template and provided arguments.
     */
    private GetPromptResult buildLocalPromptResult(Document arguments) {
        if (promptTemplate == null) {
            return GetPromptResult.builder()
                    .description(promptInfo.getDescription())
                    .messages(List.of(
                            PromptMessage.builder()
                                    .role(PromptRole.ASSISTANT.getValue())
                                    .content(PromptMessageContent.builder()
                                            .type(PromptMessageContentType.TEXT)
                                            .text("Template is required for the prompt:" + promptInfo.getName())
                                            .build())
                                    .build()))
                    .build();
        }

        var requiredArguments = getRequiredArguments();

        if (!requiredArguments.isEmpty() && arguments == null) {
            return GetPromptResult.builder()
                    .description(promptInfo.getDescription())
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

        String processedText = applyTemplateArguments(promptTemplate, arguments);

        return GetPromptResult.builder()
                .description(promptInfo.getDescription())
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
     * @param template The template string containing {{placeholder}} patterns
     * @param arguments Document containing replacement values
     * @return The template with all placeholders replaced
     */
    private String applyTemplateArguments(String template, Document arguments) {
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
     * Extracts the set of required argument names from the PromptInfo.
     */
    private Set<String> getRequiredArguments() {
        return promptInfo.getArguments()
                .stream()
                .filter(PromptArgument::isRequired)
                .map(PromptArgument::getName)
                .collect(Collectors.toSet());
    }
}
