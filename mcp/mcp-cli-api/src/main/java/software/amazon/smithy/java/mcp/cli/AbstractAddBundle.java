/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Set;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.Location;

/**
 * Abstract base class for CLI commands that add tool bundles to the Smithy MCP configuration.
 * <p>
 * Subclasses must implement methods to provide tool bundle configuration details and specify
 * whether existing configurations can be overwritten.
 *
 */
public abstract class AbstractAddBundle extends SmithyMcpCommand implements ConfigurationCommand {

    @Override
    public final void execute(ExecutionContext context) throws Exception {
        var config = context.config();
        if (!canOverwrite() && config.getToolBundles().containsKey(getToolBundleName())) {
            throw new IllegalArgumentException("Tool bundle " + getToolBundleName()
                    + " already exists. Either choose a new name or pass --overwrite to overwrite the existing tool bundle");
        }
        var newConfig = getNewToolConfig();
        ConfigUtils.addMcpBundle(config, getToolBundleName(), newConfig.mcpBundle());
        System.out.println("Added tool bundle " + getToolBundleName());
    }

    protected final Location getBundleFileLocation() {
        return Location.builder()
                .fileLocation(ConfigUtils.getBundleFileLocation(getToolBundleName()).toString())
                .build();
    }

    /**
     * Returns a new tool configuration instance to be added to the Smithy MCP config.
     *
     * @return A new tool bundle configuration
     */
    protected abstract CliBundle getNewToolConfig();

    /**
     * Returns the name under which this tool bundle will be registered.
     *
     * @return The tool bundle name
     */
    protected abstract String getToolBundleName();

    /**
     * Determines whether an existing tool bundle with the same name can be overwritten.
     *
     * @return true if existing tool bundle can be overwritten, false otherwise
     */
    protected abstract boolean canOverwrite();

    protected abstract Set<String> allowedTools();

    protected abstract Set<String> blockedTools();
}
