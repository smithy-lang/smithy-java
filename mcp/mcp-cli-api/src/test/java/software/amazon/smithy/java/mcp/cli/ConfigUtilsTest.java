/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.cli.model.ClientConfig;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.McpServerConfig;
import software.amazon.smithy.java.mcp.cli.model.McpServersClientConfig;

class ConfigUtilsTest {

    @TempDir
    private Path tempDir;

    private Path clientConfigPath;
    private Path nonExistentConfigPath;
    private Config config;

    @BeforeEach
    public void setup() throws IOException {
        clientConfigPath = tempDir.resolve("mcp-client.json");
        nonExistentConfigPath = tempDir.resolve("non-existent-client.json");

        ClientConfig clientConfig = ClientConfig.builder()
                .name("testClient")
                .filePath(clientConfigPath.toAbsolutePath().toString())
                .build();

        ClientConfig nonExistentConfig = ClientConfig.builder()
                .name("nonExistentClient")
                .filePath(nonExistentConfigPath.toAbsolutePath().toString())
                .build();

        config = Config.builder()
                .clientConfigs(List.of(clientConfig, nonExistentConfig))
                .build();

        Files.createFile(clientConfigPath);
        Files.writeString(clientConfigPath, "{}", StandardCharsets.UTF_8);
    }

    @Test
    void testAddToClientConfigs() throws IOException {
        // Setup
        McpServerConfig serverConfig = McpServerConfig.builder()
                .command("testCommand")
                .args(List.of("arg1", "arg2"))
                .env(Map.of("key", "value"))
                .build();

        // Execute
        ConfigUtils.addToClientConfigs(config, "testServer", Collections.singleton("testClient"), serverConfig);

        McpServersClientConfig mcpConfig = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(mcpConfig);
        assertEquals(1, mcpConfig.getMcpServers().size());
        assertTrue(mcpConfig.getMcpServers().containsKey("testServer"));

        Document serverConfigDoc = mcpConfig.getMcpServers().get("testServer");
        assertEquals("testCommand", serverConfigDoc.getMember("command").asString());
        assertEquals(List.of("arg1", "arg2"),
                serverConfigDoc.getMember("args").asList().stream().map(Document::asString).toList());
        assertEquals("value", serverConfigDoc.getMember("env").asStringMap().get("key").asString());
    }

    @Test
    void testAddToClientConfigs_NonExistentClientConfig() throws IOException {
        // Setup
        McpServerConfig serverConfig = McpServerConfig.builder()
                .command("testCommand")
                .build();

        // Execute
        ConfigUtils.addToClientConfigs(config, "testServer", Collections.singleton("nonExistentClient"), serverConfig);

        // Verify - nothing should happen, file should not be created
        assertFalse(Files.exists(nonExistentConfigPath));
    }

    @Test
    public void testAddToClientConfigs_WhenExistingConfigHasExtraFields() throws IOException {
        // Setup - Create existing config with extra fields
        String configJson = """
                {
                  "mcpServers": {
                    "existingServer": {
                      "command": "existingCommand",
                      "extraField": "extraValue"
                    }
                  }
                }
                """;
        Files.writeString(clientConfigPath, configJson, StandardCharsets.UTF_8);

        // New server config
        McpServerConfig serverConfig = McpServerConfig.builder()
                .command("newCommand")
                .build();

        // Execute
        ConfigUtils.addToClientConfigs(config, "testServer", Collections.singleton("testClient"), serverConfig);

        // Verify
        McpServersClientConfig updatedConfig = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(updatedConfig);

        // Ensure both servers exist
        assertEquals(2, updatedConfig.getMcpServers().size());

        // Ensure existing server still has extra field
        Document existingServerDoc = updatedConfig.getMcpServers().get("existingServer");
        assertEquals("existingCommand", existingServerDoc.getMember("command").asString());
        assertEquals("extraValue", existingServerDoc.getMember("extraField").asString());

        // Ensure new server was added correctly
        Document newServerDoc = updatedConfig.getMcpServers().get("testServer");
        assertEquals("newCommand", newServerDoc.getMember("command").asString());
    }

    @Test
    void testRemoveFromClientConfigs_NonExistentServer() throws IOException {
        // Setup - create client config with one server
        String configJson = """
                {
                  "mcpServers": {
                    "existingServer": {
                      "command": "existingCommand"
                    }
                  }
                }
                """;
        Files.writeString(clientConfigPath, configJson, StandardCharsets.UTF_8);

        // Execute - try to remove a non-existent server
        ConfigUtils.removeFromClientConfigs(config, "nonExistentServer", Collections.singleton("testClient"));

        // Verify - original server should still exist
        McpServersClientConfig updatedConfig = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(updatedConfig);

        assertEquals(1, updatedConfig.getMcpServers().size());
        assertTrue(updatedConfig.getMcpServers().containsKey("existingServer"));
    }

    @Test
    void testRemoveFromClientConfigs_ExistingServer() throws IOException {
        // Setup - create client config with two servers
        String configJson = """
                {
                  "mcpServers": {
                    "server1": {
                      "command": "command1"
                    },
                    "server2": {
                      "command": "command2"
                    }
                  }
                }
                """;
        Files.writeString(clientConfigPath, configJson, StandardCharsets.UTF_8);

        // Execute - remove server1
        ConfigUtils.removeFromClientConfigs(config, "server1", Collections.singleton("testClient"));

        // Verify - only server2 should remain
        McpServersClientConfig updatedConfig = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(updatedConfig);

        assertEquals(1, updatedConfig.getMcpServers().size());
        assertFalse(updatedConfig.getMcpServers().containsKey("server1"));
        assertTrue(updatedConfig.getMcpServers().containsKey("server2"));
    }

    @Test
    void testAddToClientConfigs_UpdateExistingServer() throws IOException {
        // Setup - create client config with existing server
        String configJson = """
                {
                  "mcpServers": {
                    "testServer": {
                      "command": "oldCommand",
                      "args": ["oldArg"]
                    }
                  }
                }
                """;
        Files.writeString(clientConfigPath, configJson, StandardCharsets.UTF_8);

        // New server config to update the existing one
        McpServerConfig serverConfig = McpServerConfig.builder()
                .command("newCommand")
                .args(List.of("newArg1", "newArg2"))
                .build();

        // Execute - update the existing server
        ConfigUtils.addToClientConfigs(config, "testServer", Collections.singleton("testClient"), serverConfig);

        // Verify
        McpServersClientConfig updatedConfig = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(updatedConfig);

        assertEquals(1, updatedConfig.getMcpServers().size());

        // Check that the server was updated and extraField was removed
        Document serverDoc = updatedConfig.getMcpServers().get("testServer");
        assertEquals("newCommand", serverDoc.getMember("command").asString());
        assertEquals(List.of("newArg1", "newArg2"),
                serverDoc.getMember("args").asList().stream().map(Document::asString).toList());
    }

    @Test
    void testAddToClientConfigs_EmptyClientSet() throws IOException {
        // Setup - create a second client config path
        Path clientConfigPath2 = tempDir.resolve("client-config2.json");
        Files.createFile(clientConfigPath2);
        Files.writeString(clientConfigPath2, "{}", StandardCharsets.UTF_8);

        ClientConfig clientConfig2 = ClientConfig.builder()
                .name("testClient2")
                .filePath(clientConfigPath2.toAbsolutePath().toString())
                .build();

        // Create Config with multiple ClientConfigs
        Config multiConfig = Config.builder()
                .clientConfigs(List.of(
                        config.getClientConfigs().get(0),
                        clientConfig2))
                .build();

        McpServerConfig serverConfig = McpServerConfig.builder()
                .command("testCommand")
                .build();

        // Execute with empty client set (should update all clients)
        ConfigUtils.addToClientConfigs(multiConfig, "testServer", Collections.emptySet(), serverConfig);

        // Verify both client configs were updated
        McpServersClientConfig config1 = ConfigUtils.getClientConfig(clientConfigPath);
        assertNotNull(config1);
        assertEquals(1, config1.getMcpServers().size());
        assertTrue(config1.getMcpServers().containsKey("testServer"));

        McpServersClientConfig config2 = ConfigUtils.getClientConfig(clientConfigPath2);
        assertNotNull(config2);
        assertEquals(1, config2.getMcpServers().size());
        assertTrue(config2.getMcpServers().containsKey("testServer"));
    }

    @Test
    void testRemoveFromClientConfigs_NonExistentClientConfig() throws IOException {
        // Setup
        assertFalse(Files.exists(nonExistentConfigPath));

        // Execute
        ConfigUtils.removeFromClientConfigs(config, "testServer", Collections.singleton("nonExistentClient"));

        // Verify - nothing should happen, file should not be created
        assertFalse(Files.exists(nonExistentConfigPath));
    }
}
