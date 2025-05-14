/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;
import software.amazon.smithy.modelbundle.api.ModelBundles;

public class McpBundles {

    private McpBundles() {}

    public static Service getService(Bundle bundle) {
        return switch (bundle.type()) {
            case smithyBundle -> {
                SmithyMcpBundle smithyMcpBundle = bundle.getValue();
                yield ModelBundles.getService(smithyMcpBundle.getBundle());
            }
            default -> throw new IllegalArgumentException("Unsupported bundle type: " + bundle.type());
        };
    }
}
