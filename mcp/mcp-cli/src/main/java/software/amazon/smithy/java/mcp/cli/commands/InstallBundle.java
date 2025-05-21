/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.RegistryUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.McpServerConfig;
import software.amazon.smithy.java.mcp.cli.model.McpServersClientConfig;

@Command(name = "install", description = "Downloads and adds a bundle from the MCP registry.")
public class InstallBundle extends SmithyMcpCommand {

    private static final JsonCodec JSON_CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .prettyPrint(true)
                    .build())
            .build();

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided it will use the default registry.")
    String registry;

    @Option(names = {"-n", "--name"}, description = "Name of the MCP Bundle to install.")
    String name;

    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated")
    List<String> clients;

    @Option(names = "--print-only",
            description = "If specified will not edit the client configs and only print to console.")
    boolean print;

    @Override
    protected void execute(Config config) throws IOException {
        if (registry != null && !config.getRegistries().containsKey(registry)) {
            throw new IllegalArgumentException("The registry '" + registry + "' does not exist.");
        }
        var bundle = RegistryUtils.getRegistry(registry).getMcpBundle(name);
        ConfigUtils.addMcpBundle(config, name, bundle);
        var newConfig = McpServerConfig.builder().command("mcp-registry").args(List.of("start-server", name)).build();
        if (print) {
            System.out.println(newConfig);
            return;
        }
        for (var clientConfigs : config.getClientConfigs()) {
            var filePath = clientConfigs.getFilePath();
            var currentConfig = Files.readAllBytes(Path.of(filePath));
            McpServersClientConfig currentMcpConfig;
            if (currentConfig.length == 0) {
                currentMcpConfig = McpServersClientConfig.builder().build();
            } else {
                currentMcpConfig =
                        McpServersClientConfig.builder()
                                .deserialize(JSON_CODEC.createDeserializer(currentConfig))
                                .build();
            }
            var map = new LinkedHashMap<>(currentMcpConfig.getMcpServers());
            map.put(name, newConfig);
            var newMcpConfig = McpServersClientConfig.builder().mcpServers(map).build();
            Files.write(Path.of(filePath),
                    ByteBufferUtils.getBytes(JSON_CODEC.serialize(newMcpConfig)),
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}
