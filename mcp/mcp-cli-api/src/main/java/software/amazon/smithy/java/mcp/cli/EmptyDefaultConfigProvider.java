/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.List;
import software.amazon.smithy.java.mcp.cli.model.ClientConfig;
import software.amazon.smithy.java.mcp.cli.model.Config;

public final class EmptyDefaultConfigProvider implements DefaultConfigProvider {

    @Override
    public Config getConfig() {
        return Config.builder()
                .clientConfigs(List.of(ClientConfig.builder()
                        .name("q-cli")
                        .filePath(ConfigUtils.resolveFromHomeDir(".aws", "amazonq", "mcp.json").toString())
                        .build()))
                .build();
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }
}
