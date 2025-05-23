/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static software.amazon.smithy.java.io.ByteBufferUtils.getBytes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ServiceLoader;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.Location;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;

/**
 * Utility class for managing Smithy MCP configuration files.
 * <p>
 * This class provides methods for loading, creating, updating, and serializing MCP configurations.
 */
public class ConfigUtils {

    private ConfigUtils() {}

    private static final JsonCodec JSON_CODEC = JsonCodec.builder().build();

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
        var mcpBundleConfig = McpBundleConfig.builder()
                .smithyModeled(SmithyModeledBundleConfig.builder()
                        .name(toolBundleName)
                        .bundleLocation(Location.builder()
                                .fileLocation(ConfigUtils.getBundleFileLocation(toolBundleName).toString())
                                .build())
                        .build())
                .build();
        addMcpBundle(config, toolBundleName, new CliBundle(bundle, mcpBundleConfig));
        addMcpBundleConfig(config, toolBundleName, mcpBundleConfig);
        return mcpBundleConfig;
    }
}
