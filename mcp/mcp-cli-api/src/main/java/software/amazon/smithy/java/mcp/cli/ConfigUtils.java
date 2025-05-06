/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.ToolBundleConfig;

/**
 * Utility class for managing Smithy MCP configuration files.
 * <p>
 * This class provides methods for loading, creating, updating, and serializing MCP configurations.
 */
public class ConfigUtils {

    private ConfigUtils() {}

    private static final JsonCodec JSON_CODEC = JsonCodec.builder().build();

    /**
     * Gets the path to the config file.
     *
     * @return The path to the config file
     */
    private static Path getConfigPath() {
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".config", "smithy-mcp");
        return configDir.resolve("config.json");
    }

    /**
     * Ensures the config directory exists.
     *
     * @throws IOException If there's an error creating directories
     */
    private static void ensureConfigDirExists() throws IOException {
        Path configDir = getConfigPath();
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }
    }

    /**
     * Loads an existing config file or creates one if it doesn't exist.
     *
     * @return The config file
     * @throws IOException If there's an error creating directories or the file
     */
    public static Config loadOrCreateConfig() throws IOException {
        Path configFile = getConfigPath();
        ensureConfigDirExists();

        // Check if the config file exists, create it if it doesn't
        var file = configFile.toFile();
        if (!file.exists()) {
            // Create an empty JSON object as the default config
            try (var writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write("{}");
            }
        }

        return fromJson(Files.readAllBytes(configFile));
    }

    /**
     * Updates the MCP configuration file with the provided configuration.
     *
     * @param config The configuration to write to the file
     * @throws IOException If there's an error writing to the file
     */
    public static void updateConfig(Config config) throws IOException {
        Path configFile = getConfigPath();
        ensureConfigDirExists();

        var file = configFile.toFile();
        try (var writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(ByteBufferUtils.asString(JSON_CODEC.serialize(config)));
        }
    }

    /**
     * Deserializes a Config object from JSON bytes.
     *
     * @param json The JSON data as a byte array
     * @return The deserialized Config object with defaults applied
     */
    public static Config fromJson(byte[] json) {
        return adjustDefaults(Config.builder().deserialize(JSON_CODEC.createDeserializer(json)).build());
    }

    /**
     * Applies default values to a configuration if needed.
     *
     * @param config The configuration to adjust
     * @return The configuration with defaults applied
     */
    private static Config adjustDefaults(Config config) {
        return config;
    }

    /**
     * Adds a new tool bundle configuration to an existing configuration and saves it.
     *
     * @param existingConfig The existing configuration to update
     * @param name The name under which to register the tool bundle
     * @param toolBundleConfig The tool bundle configuration to add
     * @throws IOException If there's an error writing the updated configuration
     */
    public static void addToolConfig(Config existingConfig, String name, ToolBundleConfig toolBundleConfig)
            throws IOException {
        var existingToolBundles = new HashMap<>(existingConfig.getToolBundles());
        existingToolBundles.put(name, toolBundleConfig);
        var newConfig = existingConfig.toBuilder().toolBundles(existingToolBundles).build();
        updateConfig(newConfig);
    }

    public static void main(String[] args) throws IOException {
        System.out.println(loadOrCreateConfig());
    }
}
