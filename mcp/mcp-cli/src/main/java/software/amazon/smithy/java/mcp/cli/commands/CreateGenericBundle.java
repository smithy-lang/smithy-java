/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.ArgGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.mcp.cli.AbstractCreateBundle;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
import software.amazon.smithy.mcp.bundle.api.model.ExecSpec;
import software.amazon.smithy.mcp.bundle.api.model.GenericArtifact;
import software.amazon.smithy.mcp.bundle.api.model.GenericBundle;

@Command(name = "generic-mcp", description = "Create a generic MCP server with custom executable commands.")
public class CreateGenericBundle extends AbstractCreateBundle<CreateGenericBundle.CreateGenericBundleInput> {

    @ArgGroup(multiplicity = "1")
    Input input;

    @Override
    protected CreateGenericBundleInput getInput() {
        return input.cliInput;
    }

    @Override
    protected Bundle getNewBundle(CreateGenericBundleInput input) {
        var genericBundleBuilder = GenericBundle.builder()
                .metadata(BundleMetadata.builder()
                        .id(input.id)
                        .name(input.name)
                        .description(input.description)
                        .build())
                .artifact(new GenericArtifact.EmptyMember())
                .executeDirectly(true);

        // Parse and add install commands
        if (input.installCommands != null && !input.installCommands.isEmpty()) {
            List<ExecSpec> installSpecs = new ArrayList<>();
            for (String installCommand : input.installCommands) {
                installSpecs.add(parseExecSpec(installCommand));
            }
            genericBundleBuilder.install(installSpecs);
        }

        // Parse and add run command
        if (input.runCommand != null) {
            genericBundleBuilder.run(parseExecSpec(input.runCommand));
        }

        return Bundle.builder()
                .genericBundle(genericBundleBuilder.build())
                .build();
    }

    private ExecSpec parseExecSpec(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty command provided");
        }

        String executable = parts[0];
        List<String> args = new ArrayList<>();
        if (parts.length > 1) {
            args.addAll(Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length)));
        }

        return ExecSpec.builder()
                .executable(executable)
                .args(args.isEmpty() ? null : args)
                .build();
    }

    public static class Input {
        @ArgGroup(exclusive = false)
        CreateGenericBundleInput cliInput;

        @ArgGroup(multiplicity = "1")
        JsonInput jsonInput;
    }

    public static class CreateGenericBundleInput extends CreateBundleInput {
        @Option(names = {"-i", "--install"},
                arity = "1..*",
                description = "Install commands to run during setup. " +
                        "Example: --install 'npm install' 'pip install -r requirements.txt'")
        protected List<String> installCommands;

        @Option(names = {"-r", "--run"},
                description = "The main command to run the MCP server. " +
                        "Example: --run 'node server.js' or --run 'python main.py'",
                required = true)
        protected String runCommand;
    }
}
