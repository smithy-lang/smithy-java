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
public sealed interface McpOutcome
        permits McpOutcome.Success, McpOutcome.Failure, McpOutcome.Subscribed, McpOutcome.NoResponse {

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

    /**
     * A {@code subscriptions/listen} request was accepted.
     *
     * <p>No JSON-RPC response is written now. The transport must first send
     * {@code notifications/subscriptions/acknowledged} with {@code accepted}, then deliver only those
     * notification types tagged with the subscription id, and keep the request open until the client
     * cancels it or the transport ends it.
     *
     * @param id the JSON-RPC id of the listen request, which is also the subscription id
     * @param accepted the requested notification types this server will deliver
     */
    record Subscribed(Document id, McpSubscriptionFilter accepted) implements McpOutcome {
        public Subscribed {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(accepted, "accepted");
        }
    }

    enum NoResponse implements McpOutcome {
        INSTANCE
    }
}
