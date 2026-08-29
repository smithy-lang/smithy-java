/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * The result of blocking MCP execution.
 */
@SmithyUnstableApi
public sealed interface McpOutcome permits McpOutcome.Success, McpOutcome.Failure, McpOutcome.NoResponse {

    record Success(Document id, Document result) implements McpOutcome {
        public Success {
            Objects.requireNonNull(result, "result");
        }
    }

    record Failure(Document id, McpError error) implements McpOutcome {
        public Failure {
            Objects.requireNonNull(error, "error");
        }
    }

    enum NoResponse implements McpOutcome {
        INSTANCE
    }
}
