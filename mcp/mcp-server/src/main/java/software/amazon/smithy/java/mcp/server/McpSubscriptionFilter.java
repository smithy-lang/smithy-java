/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * The notification types requested by, or acknowledged for, a {@code subscriptions/listen} stream.
 *
 * @param toolsListChanged whether {@code notifications/tools/list_changed} is included
 * @param promptsListChanged whether {@code notifications/prompts/list_changed} is included
 * @param resourcesListChanged whether {@code notifications/resources/list_changed} is included
 * @param resourceSubscriptions resource URIs whose {@code notifications/resources/updated} are included
 */
@SmithyUnstableApi
public record McpSubscriptionFilter(
        boolean toolsListChanged,
        boolean promptsListChanged,
        boolean resourcesListChanged,
        List<String> resourceSubscriptions) {
    /**
     * A filter that includes no notification types.
     */
    public static final McpSubscriptionFilter NONE = new McpSubscriptionFilter(false, false, false, List.of());

    public McpSubscriptionFilter {
        resourceSubscriptions = resourceSubscriptions == null ? List.of() : List.copyOf(resourceSubscriptions);
    }

    static McpSubscriptionFilter decode(Document document) {
        if (document == null || !(document.isType(ShapeType.MAP) || document.isType(ShapeType.STRUCTURE))) {
            throw new McpProtocolException(-32602, "Missing or invalid notifications filter");
        }
        var uris = document.getMember("resourceSubscriptions");
        List<String> resources = List.of();
        if (uris != null) {
            if (!uris.isType(ShapeType.LIST)) {
                throw new McpProtocolException(-32602, "resourceSubscriptions must be an array of strings");
            }
            resources = uris.asList().stream().map(value -> {
                if (!value.isType(ShapeType.STRING)) {
                    throw new McpProtocolException(-32602, "resourceSubscriptions must be an array of strings");
                }
                return value.asString();
            }).toList();
        }
        return new McpSubscriptionFilter(
                flag(document, "toolsListChanged"),
                flag(document, "promptsListChanged"),
                flag(document, "resourcesListChanged"),
                resources);
    }

    private static boolean flag(Document document, String name) {
        var value = document.getMember(name);
        if (value == null) {
            return false;
        }
        if (!value.isType(ShapeType.BOOLEAN)) {
            throw new McpProtocolException(-32602, name + " must be a boolean");
        }
        return value.asBoolean();
    }

    /**
     * Returns the subset of this filter that is also enabled in {@code supported}.
     */
    public McpSubscriptionFilter intersect(McpSubscriptionFilter supported) {
        return new McpSubscriptionFilter(
                toolsListChanged && supported.toolsListChanged,
                promptsListChanged && supported.promptsListChanged,
                resourcesListChanged && supported.resourcesListChanged,
                resourceSubscriptions.stream().filter(supported.resourceSubscriptions::contains).toList());
    }

    Document toDocument() {
        var values = new HashMap<String, Document>();
        if (toolsListChanged) {
            values.put("toolsListChanged", Document.of(true));
        }
        if (promptsListChanged) {
            values.put("promptsListChanged", Document.of(true));
        }
        if (resourcesListChanged) {
            values.put("resourcesListChanged", Document.of(true));
        }
        if (!resourceSubscriptions.isEmpty()) {
            values.put("resourceSubscriptions",
                    Document.of(resourceSubscriptions.stream().map(Document::of).toList()));
        }
        return Document.of(Map.copyOf(values));
    }
}
