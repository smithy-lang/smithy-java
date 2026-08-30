/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Transport-specific request information available during protocol validation.
 */
@SmithyUnstableApi
public interface McpTransportContext {
    McpTransportContext STDIO = new Stdio();

    record Stdio() implements McpTransportContext {}

    record Http(Map<String, List<String>> headers, boolean loopbackOnly) implements McpTransportContext {
        public Http {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }
}
