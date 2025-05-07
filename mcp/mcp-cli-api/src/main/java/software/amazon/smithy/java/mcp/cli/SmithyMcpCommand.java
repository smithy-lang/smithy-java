/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static software.amazon.smithy.java.mcp.cli.ConfigUtils.loadOrCreateConfig;

import java.util.concurrent.Callable;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.cli.model.Config;

/**
 * Base class for all Smithy MCP CLI commands.
 * <p>
 * This class implements the Callable interface to provide a consistent execution pattern
 * for all MCP CLI commands. It handles loading the configuration, executing the command,
 * and providing appropriate error handling.
 */
public abstract class SmithyMcpCommand implements Callable<Integer> {

    InternalLogger LOG = InternalLogger.getLogger(SmithyMcpCommand.class);

    @Override
    public final Integer call() throws Exception {
        try {
            var config = loadOrCreateConfig();
            execute(config);
            return 0;
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid input : [" + e.getMessage() + "]");
            return 2;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return 1;
        }
    }

    /**
     * Execute the command with the provided configuration.
     * <p>
     * Subclasses must implement this method to provide command-specific functionality.
     *
     * @param config The MCP configuration
     * @throws Exception If an error occurs during execution
     */
    protected abstract void execute(Config config) throws Exception;
}
