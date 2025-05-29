/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static software.amazon.smithy.java.io.ByteBufferUtils.getBytes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.cli.model.ClientConfig;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.GenericToolBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.Location;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.McpServerConfig;
import software.amazon.smithy.java.mcp.cli.model.McpServersClientConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.ExecSpec;
import software.amazon.smithy.mcp.bundle.api.model.GenericBundle;

/**
 * Utility class for managing Smithy MCP configuration files.
 * <p>
 * This class provides methods for loading, creating, updating, and serializing MCP configurations.
 */
public class ConfigUtils {

    private ConfigUtils() {}

    private static final JsonCodec JSON_CODEC = JsonCodec.builder()
            .prettyPrint(true)
            .build();

    private static final Path CONFIG_DIR = resolveFromHomeDir(".config", "smithy-mcp");
    private static final Path BUNDLE_DIR = CONFIG_DIR.resolve("bundles");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");

    private static final DefaultConfigProvider DEFAULT_CONFIG_PROVIDER;

    static {
        try {
            ensureDirectoryExists(CONFIG_DIR);
            ensureDirectoryExists(BUNDLE_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DEFAULT_CONFIG_PROVIDER = ServiceLoader.load(DefaultConfigProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .min(Comparator.comparing(DefaultConfigProvider::priority))
                .orElse(new EmptyDefaultConfigProvider());
    }

    public static Path resolveFromHomeDir(String... paths) {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, paths);
    }

    /**
     * Ensures the config directory exists.
     *
     * @throws IOException If there's an error creating directories
     */
    private static void ensureDirectoryExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    /**
     * Loads an existing config file or creates one if it doesn't exist.
     *
     * @return The config file
     * @throws IOException If there's an error creating directories or the file
     */
    public static Config loadOrCreateConfig() throws IOException {

        if (!CONFIG_PATH.toFile().exists()) {
            // Create an empty JSON object as the default config
            Files.write(CONFIG_PATH, toJson(DEFAULT_CONFIG_PROVIDER.getConfig()), StandardOpenOption.CREATE);
        }

        return fromJson(Files.readAllBytes(CONFIG_PATH));
    }

    public static Path getBundleFileLocation(String bundleName) {
        return BUNDLE_DIR.resolve(bundleName + ".json");
    }

    /**
     * Updates the MCP configuration file with the provided configuration.
     *
     * @param config The configuration to write to the file
     * @throws IOException If there's an error writing to the file
     */
    public static void updateConfig(Config config) throws IOException {
        Files.write(CONFIG_PATH, toJson(config), StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Deserializes a Config object from JSON bytes.
     *
     * @param json The JSON data as a byte array
     * @return The deserialized Config object with defaults applied
     */
    private static Config fromJson(byte[] json) {
        return Config.builder().deserialize(JSON_CODEC.createDeserializer(json)).build();
    }

    private static byte[] toJson(SerializableStruct struct) {
        return getBytes(JSON_CODEC.serialize(struct));
    }

    /**
     * Adds a new tool bundle configuration to an existing configuration and saves it.
     *
     * @param existingConfig   The existing configuration to update
     * @param name             The name under which to register the tool bundle
     * @param toolBundleConfig The tool bundle configuration to add
     * @throws IOException If there's an error writing the updated configuration
     */
    private static void addMcpBundleConfig(Config existingConfig, String name, McpBundleConfig toolBundleConfig)
            throws IOException {
        var existingToolBundles = new HashMap<>(existingConfig.getToolBundles());
        existingToolBundles.put(name, toolBundleConfig);
        var newConfig = existingConfig.toBuilder().toolBundles(existingToolBundles).build();
        updateConfig(newConfig);
    }

    public static Bundle getMcpBundle(String bundleName) {
        try {
            return Bundle.builder()
                    .deserialize(JSON_CODEC.createDeserializer(Files.readAllBytes(getBundleFileLocation(bundleName))))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeMcpBundle(Config currentConfig, String bundleName) throws IOException {
        var builder = currentConfig.toBuilder();
        var newBundles = new LinkedHashMap<>(currentConfig.getToolBundles());
        newBundles.remove(bundleName);
        builder.toolBundles(newBundles);
        var newConfig = builder.build();
        updateConfig(newConfig);
        var bundleFile = getBundleFileLocation(bundleName);
        Files.deleteIfExists(bundleFile);
    }

    public static void addToClientConfigs(Config config, String name, Set<String> clients, McpServerConfig serverConfig)
            throws IOException {
        updateClientConfigs(config, name, clients, serverConfig);
    }

    public static void removeFromClientConfigs(Config config, String name, Set<String> clients) throws IOException {
        updateClientConfigs(config, name, clients, null);
    }

    private static void updateClientConfigs(Config config, String name, Set<String> clients, McpServerConfig newConfig)
            throws IOException {
        var clientConfigsToUpdate = getClientConfigsToUpdate(config, clients);
        boolean isDelete = newConfig == null;
        for (var clientConfigs : clientConfigsToUpdate) {
            var filePath = Path.of(clientConfigs.getFilePath());
            if (Files.notExists(filePath)) {
                System.out.printf("Skipping updating Mcp config file for %s as the file path '%s' does not exist.",
                        name,
                        filePath);
                continue;
            }
            var currentConfig = Files.readAllBytes(filePath);
            McpServersClientConfig currentMcpConfig;
            if (currentConfig.length == 0) {
                if (isDelete) {
                    continue;
                }
                currentMcpConfig = McpServersClientConfig.builder().build();
            } else {
                currentMcpConfig =
                        McpServersClientConfig.builder()
                                .deserialize(JSON_CODEC.createDeserializer(currentConfig))
                                .build();
            }
            var map = new LinkedHashMap<>(currentMcpConfig.getMcpServers());
            if (isDelete) {
                if (map.remove(name) == null) {
                    continue;
                }
            } else {
                map.put(name, newConfig);
            }
            var newMcpConfig = McpServersClientConfig.builder().mcpServers(map).build();
            Files.write(filePath,
                    ByteBufferUtils.getBytes(JSON_CODEC.serialize(newMcpConfig)),
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static Set<ClientConfig> getClientConfigsToUpdate(Config config, Set<String> clients) {
        Set<ClientConfig> clientConfigsToUpdate;
        if (!clients.isEmpty()) {
            clientConfigsToUpdate = config.getClientConfigs()
                    .stream()
                    .filter(c -> clients.contains(c.getName()))
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            clientConfigsToUpdate = Set.copyOf(config.getClientConfigs());
        }
        return clientConfigsToUpdate;
    }

    private static void addMcpBundle(Config config, String toolBundleName, CliBundle mcpBundleConfig)
            throws IOException {
        var serializedBundle = toJson(mcpBundleConfig.mcpBundle());
        Files.write(getBundleFileLocation(toolBundleName),
                serializedBundle,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE);
        addMcpBundleConfig(config, toolBundleName, mcpBundleConfig.mcpBundleConfig());
    }

    public static McpBundleConfig addMcpBundle(Config config, String toolBundleName, Bundle bundle)
            throws IOException {
        var location = Location.builder()
                .fileLocation(ConfigUtils.getBundleFileLocation(toolBundleName).toString())
                .build();
        var builder = McpBundleConfig.builder();
        switch (bundle.type()) {
            case smithyBundle -> builder.smithyModeled(SmithyModeledBundleConfig.builder()
                    .name(toolBundleName)
                    .bundleLocation(location)
                    .build());
            case genericBundle -> {
                GenericBundle genericBundle = bundle.getValue();
                install(genericBundle.getInstall());
                builder.genericConfig(
                        GenericToolBundleConfig.builder().name(toolBundleName).bundleLocation(location).build());
            }
            default -> throw new IllegalStateException("Unexpected bundle type: " + bundle.type());
        }

        var mcpBundleConfig = builder.build();
        addMcpBundle(config, toolBundleName, new CliBundle(bundle, mcpBundleConfig));
        addMcpBundleConfig(config, toolBundleName, mcpBundleConfig);
        return mcpBundleConfig;
    }

    private static void install(ExecSpec execSpec) {

        ProcessBuilder pb = new ProcessBuilder(execSpec.getExecutable());
        pb.command().addAll(execSpec.getArgs());
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            String output = captureProcessOutput(process);

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                throw new RuntimeException("Installation timed out after 5 minutes");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                        "Installation failed with exit code %d. Command: %s. Output: %s",
                        exitCode,
                        String.join(" ", pb.command()),
                        output));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            throw new RuntimeException("Installation was interrupted", e);

        } catch (IOException e) {
            throw new RuntimeException("Failed to start installation process: " +
                    String.join(" ", pb.command()), e);

        } finally {
            // Ensure process is cleaned up
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String captureProcessOutput(Process process) throws IOException {
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return in.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
