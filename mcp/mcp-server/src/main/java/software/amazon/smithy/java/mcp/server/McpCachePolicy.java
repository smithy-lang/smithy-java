/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Immutable cache-hint policy for MCP methods.
 */
@SmithyUnstableApi
public final class McpCachePolicy {
    public static final McpCachePolicy DEFAULT = builder().build();

    private final McpCacheHint defaultHint;
    private final Map<McpMethod.Standard, McpCacheHint> methodHints;

    private McpCachePolicy(Builder builder) {
        defaultHint = builder.defaultHint;
        methodHints = Map.copyOf(builder.methodHints);
    }

    public McpCacheHint hint(McpMethod.Standard method) {
        return methodHints.getOrDefault(method, defaultHint);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<McpMethod.Standard, McpCacheHint> methodHints =
                new EnumMap<>(McpMethod.Standard.class);
        private McpCacheHint defaultHint = McpCacheHint.NO_CACHE;

        public Builder defaultHint(McpCacheHint hint) {
            defaultHint = Objects.requireNonNull(hint, "hint");
            return this;
        }

        public Builder hint(McpMethod.Standard method, McpCacheHint hint) {
            methodHints.put(
                    Objects.requireNonNull(method, "method"),
                    Objects.requireNonNull(hint, "hint"));
            return this;
        }

        public McpCachePolicy build() {
            return new McpCachePolicy(this);
        }
    }
}
