/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.settings;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Verifies that {@link software.amazon.smithy.java.codegen.JavaCodegenIntegration#customizeSettings}
 * is invoked early enough for default plugins registered there to be wired into generated clients,
 * while default plugins registered from {@code customize} (which runs after generation) are not.
 *
 * <p>The wiring is driven by {@link DefaultPluginSettingsIntegration}, discovered via {@code ServiceLoader}.
 */
public class DefaultPluginSettingsIntegrationTest {

    private final MockManifest manifest = new MockManifest();

    @BeforeEach
    public void setup() {
        var model = Model.assembler()
                .addImport(Objects.requireNonNull(
                        DefaultPluginSettingsIntegrationTest.class.getResource("default-plugin-settings.smithy")))
                .assemble()
                .unwrap();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.java.codegen.settings#DefaultPluginSettingsService")
                        .withMember("namespace", "smithy.java.codegen.settings")
                        .withMember("modes", ArrayNode.fromStrings("client"))
                        .build())
                .model(model)
                .build();
        new JavaCodegenPlugin().execute(context);
        assertFalse(manifest.getFiles().isEmpty());
    }

    @Test
    void pluginAddedFromCustomizeSettingsIsWiredIntoClient() {
        var client = clientInterfaceSource();
        // Registered from customizeSettings() -> present as a default plugin.
        assertThat(client, containsString("new EarlyDefaultPlugin()"));
        assertThat(client,
                containsString("private final List<ClientPlugin> defaultPlugins = List.of(earlyDefaultPlugin)"));
    }

    @Test
    void pluginAddedFromCustomizeIsTooLateAndIgnored() {
        var client = clientInterfaceSource();
        // Registered from customize() -> too late, never wired in.
        assertThat(client, not(containsString("LateDefaultPlugin")));
    }

    private String clientInterfaceSource() {
        return manifest.getFiles()
                .stream()
                .map(Object::toString)
                .filter(p -> p.endsWith("DefaultPluginSettingsServiceClient.java"))
                .findFirst()
                .flatMap(p -> manifest.getFileString(Paths.get(p)))
                .orElseThrow(
                        () -> new AssertionError("Generated client interface not found in " + manifest.getFiles()));
    }
}
