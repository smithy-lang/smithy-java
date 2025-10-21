/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static software.amazon.smithy.java.io.ByteBufferUtils.getBytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
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
import software.amazon.smithy.mcp.bundle.api.model.SmithyMcpBundle;

/**
 * Utility class for managing Smithy MCP configuration files.
 * <p>
 * This class provides methods for loading, creating, updating, and serializing MCP configurations.
 */
public class ConfigUtils {

    private ConfigUtils() {}

    private static final JsonCodec JSON_CODEC = JsonCodec.builder()
            .prettyPrint(true)
            .serializeTypeInDocuments(false)
            .build();

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean WINDOWS = OS.contains("windows");

    private static final Path CONFIG_DIR = getSmithyMcpCliHome();
    private static final Path BUNDLE_DIR = CONFIG_DIR.resolve("bundles");
    private static final Path SHIMS_DIR = CONFIG_DIR.resolve("mcp-servers");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");

    private static final Map<String, String> SHELL_TO_CONFIG = Map.of(
            "zsh",
            ".zshrc",
            "bash",
            ".bashrc",
            "sh",
            ".profile",
            "dash",
            ".profile",
            "ksh",
            ".profile");

    private static final DefaultConfigProvider DEFAULT_CONFIG_PROVIDER;

    static {
        try {
            ensureDirectoryExists(CONFIG_DIR);
            ensureDirectoryExists(BUNDLE_DIR);
            ensureDirectoryExists(SHIMS_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DEFAULT_CONFIG_PROVIDER = ServiceLoader.load(DefaultConfigProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .max(Comparator.comparing(DefaultConfigProvider::priority))
                .orElse(new EmptyDefaultConfigProvider());
    }

    private static Path getSmithyMcpCliHome() {
        return Optional.ofNullable(System.getenv("SMITHY_MCP_CLI_HOME"))
                .map(Paths::get)
                .orElseGet(() -> resolveFromHomeDir(".config", "smithy-mcp"));
    }

    public static Path resolveFromHomeDir(String... paths) {
        String userHome = System.getenv("TEST_USER_HOME");
        if (userHome == null) {
            userHome = System.getProperty("user.home");
        }
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

        Config config = fromJson(Files.readAllBytes(CONFIG_PATH));

        var postProcessed = DEFAULT_CONFIG_PROVIDER.postProcessConfig(config);
        if (postProcessed.isPresent()) {
            config = postProcessed.get();
            updateConfig(config);
        }

        return config;
    }

    public static Path getBundleFileLocation(String id) {
        return BUNDLE_DIR.resolve(id + ".json");
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

    public static Bundle getMcpBundle(String id) {
        try {
            return Bundle.builder()
                    .deserialize(JSON_CODEC.createDeserializer(Files.readAllBytes(getBundleFileLocation(id))))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeMcpBundle(Config currentConfig, String id) throws IOException {
        var builder = currentConfig.toBuilder();
        var newBundles = new LinkedHashMap<>(currentConfig.getToolBundles());
        newBundles.remove(id);
        builder.toolBundles(newBundles);
        var newConfig = builder.build();
        updateConfig(newConfig);
        var bundleFile = getBundleFileLocation(id);
        Files.deleteIfExists(bundleFile);
        // Remove wrapper script if it exists
        removeWrapperScript(id);
    }

    public static void addToClientConfigs(Config config, String id, Set<String> clients, McpServerConfig serverConfig)
            throws IOException {
        updateClientConfigs(config, id, clients, serverConfig);
    }

    public static void removeFromClientConfigs(Config config, String id, Set<String> clients) throws IOException {
        updateClientConfigs(config, id, clients, null);
    }

    private static void updateClientConfigs(Config config, String id, Set<String> clients, McpServerConfig newConfig)
            throws IOException {
        var clientConfigsToUpdate = getClientConfigsToUpdate(config, clients);
        boolean isDelete = newConfig == null;
        for (var clientConfigs : clientConfigsToUpdate) {
            var filePath = Path.of(clientConfigs.getFilePath());
            if (Files.notExists(filePath)) {
                System.out.printf("Skipping updating Mcp config file for %s as the file path '%s' does not exist.",
                        id,
                        filePath);
                continue;
            }
            var currentMcpConfig = getClientConfig(filePath);

            if (currentMcpConfig == null) {
                if (isDelete) {
                    continue;
                }
                currentMcpConfig = McpServersClientConfig.builder().build();
            }

            var map = new LinkedHashMap<>(currentMcpConfig.getMcpServers());
            if (isDelete) {
                if (map.remove(id) == null) {
                    continue;
                }
            } else {
                map.put(id, Document.of(newConfig));
            }
            var newMcpConfig = McpServersClientConfig.builder().mcpServers(map).build();
            Files.write(filePath,
                    ByteBufferUtils.getBytes(JSON_CODEC.serialize(newMcpConfig)),
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    static McpServersClientConfig getClientConfig(Path filePath) throws IOException {
        var currentConfig = Files.readAllBytes(filePath);
        McpServersClientConfig currentMcpConfig;
        if (currentConfig.length == 0) {
            return null;
        } else {
            currentMcpConfig =
                    McpServersClientConfig.builder()
                            .deserialize(JSON_CODEC.createDeserializer(currentConfig))
                            .build();
        }
        return currentMcpConfig;
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

    private static void writeMcpBundle(String id, Bundle bundle)
            throws IOException {
        var serializedBundle = toJson(bundle);
        Files.write(getBundleFileLocation(id),
                serializedBundle,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE);
    }

    public static McpBundleConfig addMcpBundle(Config config, String toolBundleName, Bundle bundle)
            throws IOException {
        return addMcpBundle(config, toolBundleName, bundle, false);
    }

    public static McpBundleConfig addMcpBundle(Config config, String id, Bundle bundle, boolean isLocal)
            throws IOException {
        var location = Location.builder()
                .fileLocation(ConfigUtils.getBundleFileLocation(id).toString())
                .build();
        var builder = McpBundleConfig.builder();
        switch (bundle.type()) {
            case smithyBundle -> builder.smithyModeled(SmithyModeledBundleConfig.builder()
                    .name(id)
                    .metadata(((SmithyMcpBundle) (bundle.getValue())).getMetadata())
                    .bundleLocation(location)
                    .local(isLocal)
                    .build());
            case genericBundle -> {
                GenericBundle genericBundle = bundle.getValue();
                validate(genericBundle, id);
                install(genericBundle.getInstall());
                builder.genericConfig(
                        GenericToolBundleConfig.builder()
                                .name(id)
                                .local(isLocal)
                                .bundleLocation(location)
                                .metadata(genericBundle.getMetadata())
                                .build());
            }
            default -> throw new IllegalStateException("Unexpected bundle type: " + bundle.type());
        }

        var mcpBundleConfig = builder.build();
        writeMcpBundle(id, bundle);
        addMcpBundleConfig(config, id, mcpBundleConfig);
        return mcpBundleConfig;
    }

    private static void validate(GenericBundle genericBundle, String id) {
        if (!genericBundle.isExecuteDirectly() &&
                genericBundle.getRun().getExecutable().equals(id)) {
            throw new IllegalStateException(
                    "The generic MCP run command has the same value as id which isn't allowed.");
        }

    }

    private static void install(List<ExecSpec> execSpecs) {

        for (var execSpec : execSpecs) {
            ProcessBuilder pb = new ProcessBuilder(execSpec.getExecutable());
            pb.command().addAll(execSpec.getArgs());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = null;
            try {
                process = pb.start();

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
                            String.join(" ", pb.command())));
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
    }

    public static void createWrapperScript(String id) throws IOException {
        Path scriptPath = SHIMS_DIR.resolve(id);
        String scriptContent = createScriptContent(id);

        Files.writeString(scriptPath, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        scriptPath.toFile().setExecutable(true);
    }

    private static String createScriptContent(String bundleName) {
        if (WINDOWS) {
            return "@echo off\nmcp-registry start-server " + bundleName + " %*\n";
        } else {
            return "#!/bin/bash\nexec mcp-registry start-server " + bundleName + " \"$@\"\n";
        }
    }

    public static void removeWrapperScript(String id) throws IOException {
        Path scriptPath = SHIMS_DIR.resolve(id);
        Files.deleteIfExists(scriptPath);
    }

    public static boolean isMcpServersDirInPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }

        String shimsDirStr = SHIMS_DIR.toAbsolutePath().toString();
        return pathEnv.contains(shimsDirStr);
    }

    public static void ensureMcpServersDirInPath() {
        if (isMcpServersDirInPath()) {
            return;
        }

        String shimsDirStr = SHIMS_DIR.toAbsolutePath().toString();

        if (WINDOWS) {
            printWindowsPathInstructions(shimsDirStr);
        } else {
            if (!tryAddToShellConfigs(shimsDirStr)) {
                printUnixPathInstructions(shimsDirStr);
            }
        }
    }

    private static boolean tryAddToShellConfigs(String shimsDirStr) {
        var pathExport = "export PATH=\"" + shimsDirStr + ":$PATH\"";
        var comment = "# Added by smithy-mcp";

        // Get the current shell from SHELL environment variable
        String shellPath = System.getenv("SHELL");
        String configFile = null;

        if (shellPath != null) {
            // Extract shell name from path (e.g., "/bin/zsh" -> "zsh")
            String shellName = shellPath.substring(shellPath.lastIndexOf('/') + 1);
            configFile = SHELL_TO_CONFIG.get(shellName);
        }

        // If we don't have a mapping for the shell or SHELL is not set, fall back to original behavior
        if (configFile == null) {
            return false;
        }

        var configPath = resolveFromHomeDir(configFile);

        if (Files.exists(configPath) && Files.isWritable(configPath)) {
            try {
                // Check if already present
                var lines = new ArrayList<>(Files.readAllLines(configPath, StandardCharsets.UTF_8));
                boolean alreadyPresent = lines.stream()
                        .anyMatch(line -> line.contains(shimsDirStr)
                                && line.contains("PATH")
                                && !(line.trim().startsWith("#")));

                if (!alreadyPresent) {
                    // Add the export statement
                    var newLines = new ArrayList<String>();
                    newLines.add("");
                    newLines.add(comment);
                    newLines.add(pathExport);

                    Files.write(configPath,
                            newLines,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND);

                    System.out.println("Added mcp-servers directory to PATH in " + configFile);
                    System.out.println(
                            "Please restart your shell or run 'source ~/" + configFile + "' to reload your PATH");
                    return true;
                }
                return true; // Already present, so consider it successful
            } catch (IOException e) {
                // Fall through to return false
            }
        }
        return false;
    }

    private static void printWindowsPathInstructions(String shimsDirStr) {
        System.out.println("\nTo use the installed bundle as a command, add the mcp-servers directory to your PATH:");
        System.out.println("  set PATH=" + shimsDirStr + ";%PATH%");
        System.out.println("Or permanently add it through System Properties > Environment Variables");
        System.out.println();
    }

    private static void printUnixPathInstructions(String shimsDirStr) {
        System.out.println("\nTo use the installed bundle as a command, add the mcp-servers directory to your PATH:");
        System.out.println("  export PATH=\"" + shimsDirStr + ":$PATH\"");
        System.out.println("Add this line to your shell profile (~/.bashrc, ~/.zshrc, etc.) to make it permanent");
        System.out.println();
    }

    public static void createWrapperAndUpdateClientConfigs(
            String id,
            Bundle bundle,
            Config config,
            ClientsInput input
    ) throws IOException {
        boolean shouldCreateWrapper = true;
        List<String> args = List.of();
        String command = id;
        if (bundle.getValue() instanceof GenericBundle genericBundle && genericBundle.isExecuteDirectly()) {
            command = genericBundle.getRun().getExecutable();
            args = genericBundle.getRun().getArgs();
            shouldCreateWrapper = false;
        }

        if (shouldCreateWrapper) {
            createWrapperScript(id);
            ensureMcpServersDirInPath();
        }

        var newClientConfig = McpServerConfig.builder()
                .command(command)
                .args(args)
                .build();
        //By default, print the output if there are no configured client configs.
        Set<String> clientConfigs;
        boolean print;
        if (input == null) {
            clientConfigs = config.getClientConfigs().stream().map(ClientConfig::getName).collect(Collectors.toSet());
            print = clientConfigs.isEmpty();
        } else {
            print = input.print;
            clientConfigs = input.clients;
        }

        if (print) {
            System.out.println("You can add the following to your MCP Servers config to use " + id);
            var serializedConfig = ByteBufferUtils.getBytes(JSON_CODEC.serialize(newClientConfig));
            System.out.println(new String(serializedConfig, StandardCharsets.UTF_8));
        } else {
            addToClientConfigs(config, id, clientConfigs, newClientConfig);
        }
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }
}
