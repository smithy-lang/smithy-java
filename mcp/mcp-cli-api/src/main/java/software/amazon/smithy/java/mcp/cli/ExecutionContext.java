/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.nio.file.Path;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.mcp.bundle.api.Registry;

public record ExecutionContext(Config config, Registry registry, CliMetrics metrics, Path configDir) {

    public ExecutionContext(Config config, Registry registry, CliMetrics metrics) {
        this(config, registry, metrics, ConfigUtils.getConfigDir());
    }
}
