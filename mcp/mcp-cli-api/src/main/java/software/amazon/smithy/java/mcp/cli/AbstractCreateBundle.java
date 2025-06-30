/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static picocli.CommandLine.Option;

import picocli.CommandLine.ArgGroup;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;

public abstract class AbstractCreateBundle<T extends AbstractCreateBundle.CreateBundleInput> extends SmithyMcpCommand {

    public abstract static class CreateBundleInput {
        @Option(names = "--overwrite",
                description = "Overwrite existing MCP server.",
                defaultValue = "false")
        boolean overwrite;

        @Option(names = {"--id"},
                description = "Id to assign to the MCP server. This id needs to end in mcp. Eg: (aws-dynamodb-mcp)",
                required = true)
        public String id;

        @Option(names = {"--name"}, description = "Name for this MCP server. Eg: (Pipelines MCP Server)")
        public String name;

        @Option(names = {"-d", "--description"}, description = "Description of this mcp server.")
        public String description;

        @ArgGroup
        ClientsInput clientsInput;
    }

    public static class JsonInput {
        @Option(names = {"--input"}, description = "Provide the input in json format.", required = true)
        String inputJson;

        @Option(names = {"--input-file"}, description = "Provide path to a file which contains input in json format.",
                required = true)
        String inputJsonFile;
    }

    @Override
    protected void execute(ExecutionContext context) throws Exception {
        var input = getInput();
        var config = context.config();
        if (!input.overwrite && config.getToolBundles().containsKey(input.id)) {
            throw new IllegalArgumentException("Tool bundle " + input.id
                    + " already exists. Either choose a new name or pass --overwrite to overwrite the existing tool bundle");
        }

        if (input.name == null) {
            input.name = input.id;
        }
        if (input.description == null) {
            input.description = "[" + input.id + "]" + " MCP Server";
        }

        var bundle = getNewBundle(input);
        ConfigUtils.addMcpBundle(config, input.id, bundle, true);
        ConfigUtils.createWrapperAndUpdateClientConfigs(input.id, bundle, config, input.clientsInput);
        System.out.println("Successfully created mcp server " + input.id);
    }

    protected abstract T getInput();

    protected abstract Bundle getNewBundle(T input);
}
