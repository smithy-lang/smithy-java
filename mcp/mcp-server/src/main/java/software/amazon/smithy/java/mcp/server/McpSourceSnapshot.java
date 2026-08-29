/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Map;

record McpSourceSnapshot(
        Map<String, McpToolDescriptor> tools,
        Map<String, McpPromptDescriptor> prompts,
        SmithyDocumentAdapter documentAdapter) {}
