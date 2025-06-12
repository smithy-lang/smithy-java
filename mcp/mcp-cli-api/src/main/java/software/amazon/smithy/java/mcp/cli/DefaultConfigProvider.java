/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Optional;
import software.amazon.smithy.java.mcp.cli.model.Config;

public interface DefaultConfigProvider {
    Config getConfig();

    int priority();

    /**
     * Post-process the config to have final say in the config. Allows implementations to adjust
     * the config before being adjusted by the commands.
     *
     * @return Optional containing the modified config if changes were made, empty if no changes.
     * If
     */
    default Optional<Config> postProcessConfig(Config config) {
        return Optional.empty();
    }
}
