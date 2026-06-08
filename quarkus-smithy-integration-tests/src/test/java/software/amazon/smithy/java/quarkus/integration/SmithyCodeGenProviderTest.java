/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.quarkus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.deployment.CodeGenContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.quarkus.deployment.SmithyCodeGenProvider;

/**
 * Exercises {@link SmithyCodeGenProvider} end-to-end without booting a Quarkus
 * application: builds a synthetic project layout in a temporary directory,
 * invokes {@code trigger(...)}, and asserts the expected generated Java files
 * landed in {@code outDir}.
 *
 * <p>Booting a real Quarkus app requires applying the io.quarkus Gradle plugin
 * to this module, which in turn requires {@code pluginManagement}
 * registrations in the root settings file. That's deliberately out of scope
 * for this experimental extension's first cut.
 */
class SmithyCodeGenProviderTest {

    @Test
    void generatesServerSourcesFromSmithyBuildJson(@TempDir Path projectRoot) throws Exception {
        // Lay out a minimal Smithy project: model + smithy-build.json.
        Path modelDir = Files.createDirectories(projectRoot.resolve("src/main/smithy"));
        Files.writeString(
                modelDir.resolve("coffee.smithy"),
                """
                        $version: "2"
                        namespace com.example

                        use aws.protocols#restJson1

                        @restJson1
                        service CoffeeShop {
                            operations: [GetMenu]
                        }

                        @http(method: "GET", uri: "/menu")
                        @readonly
                        operation GetMenu {
                            output := { items: CoffeeItems }
                        }

                        list CoffeeItems { member: CoffeeItem }

                        structure CoffeeItem {
                            @required
                            name: String
                        }
                        """);

        Files.writeString(
                projectRoot.resolve("smithy-build.json"),
                """
                        {
                          "version": "1.0",
                          "sources": ["src/main/smithy"],
                          "plugins": {
                            "java-codegen": {
                              "service": "com.example#CoffeeShop",
                              "namespace": "com.example.generated",
                              "modes": ["server"]
                            }
                          }
                        }
                        """);

        Path workDir = Files.createDirectories(projectRoot.resolve("build"));
        Path outDir = Files.createDirectories(workDir.resolve("generated-sources/smithy"));
        // Quarkus passes inputDir as <project>/src/main/<providerId>; the provider
        // uses that path to walk back to the project root.
        Path inputDir = projectRoot.resolve("src/main/smithy");

        CodeGenContext context = new CodeGenContext(
                /* model */ null,
                outDir,
                workDir,
                inputDir,
                /* redirectIO */ false,
                /* config */ null,
                /* test */ false);

        SmithyCodeGenProvider provider = new SmithyCodeGenProvider();
        boolean generated = provider.trigger(context);

        assertThat(generated).isTrue();

        // The generated CoffeeShop service stub lands at
        // outDir/com/example/generated/service/CoffeeShop.java.
        Path coffeeShopJava = outDir.resolve("com/example/generated/service/CoffeeShop.java");
        assertThat(coffeeShopJava).exists();

        Path getMenuOperation = outDir.resolve("com/example/generated/service/GetMenuOperation.java");
        assertThat(getMenuOperation).exists();

        Path getMenuInput = outDir.resolve("com/example/generated/model/GetMenuInput.java");
        assertThat(getMenuInput).exists();
    }

    @Test
    void noSmithyBuildJsonShortCircuits(@TempDir Path projectRoot) throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/smithy"));
        Path workDir = Files.createDirectories(projectRoot.resolve("build"));
        Path outDir = Files.createDirectories(workDir.resolve("generated-sources/smithy"));
        Path inputDir = projectRoot.resolve("src/main/smithy");

        CodeGenContext context = new CodeGenContext(
                /* model */ null,
                outDir,
                workDir,
                inputDir,
                /* redirectIO */ false,
                /* config */ null,
                /* test */ false);

        SmithyCodeGenProvider provider = new SmithyCodeGenProvider();
        assertThat(provider.trigger(context)).isFalse();
    }

    @Test
    void generatesClientSourcesFromSmithyBuildJson(@TempDir Path projectRoot) throws Exception {
        Path modelDir = Files.createDirectories(projectRoot.resolve("src/main/smithy"));
        Files.writeString(
                modelDir.resolve("coffee.smithy"),
                """
                        $version: "2"
                        namespace com.example

                        use aws.protocols#restJson1

                        @restJson1
                        service CoffeeShop {
                            operations: [GetMenu]
                        }

                        @http(method: "GET", uri: "/menu")
                        @readonly
                        operation GetMenu {
                            output := { items: CoffeeItems }
                        }

                        list CoffeeItems { member: CoffeeItem }

                        structure CoffeeItem {
                            @required
                            name: String
                        }
                        """);

        Files.writeString(
                projectRoot.resolve("smithy-build.json"),
                """
                        {
                          "version": "1.0",
                          "sources": ["src/main/smithy"],
                          "plugins": {
                            "java-codegen": {
                              "service": "com.example#CoffeeShop",
                              "namespace": "com.example.generated",
                              "protocol": "aws.protocols#restJson1",
                              "modes": ["client"]
                            }
                          }
                        }
                        """);

        Path workDir = Files.createDirectories(projectRoot.resolve("build"));
        Path outDir = Files.createDirectories(workDir.resolve("generated-sources/smithy"));
        Path inputDir = projectRoot.resolve("src/main/smithy");

        CodeGenContext context = new CodeGenContext(
                /* model */ null,
                outDir,
                workDir,
                inputDir,
                /* redirectIO */ false,
                /* config */ null,
                /* test */ false);

        boolean generated = new SmithyCodeGenProvider().trigger(context);

        assertThat(generated).isTrue();
        // Generated client lands at outDir/com/example/generated/client/CoffeeShopClient.java.
        assertThat(outDir.resolve("com/example/generated/client/CoffeeShopClient.java")).exists();
        assertThat(outDir.resolve("com/example/generated/model/GetMenuInput.java")).exists();
        // No server-side stubs in client-only mode.
        assertThat(outDir.resolve("com/example/generated/service/CoffeeShop.java")).doesNotExist();
        assertThat(outDir.resolve("com/example/generated/service/GetMenuOperation.java")).doesNotExist();
    }

    @Test
    void generatesTypesOnlyFromSmithyBuildJson(@TempDir Path projectRoot) throws Exception {
        Path modelDir = Files.createDirectories(projectRoot.resolve("src/main/smithy"));
        Files.writeString(
                modelDir.resolve("menu.smithy"),
                """
                        $version: "2"
                        namespace com.example

                        structure Menu {
                            @required
                            items: MenuItems
                        }

                        list MenuItems { member: MenuItem }

                        structure MenuItem {
                            @required
                            name: String

                            @required
                            price: Integer
                        }
                        """);

        // No `service` field — TypeCodegenSettings synthesizes one for types-only mode.
        Files.writeString(
                projectRoot.resolve("smithy-build.json"),
                """
                        {
                          "version": "1.0",
                          "sources": ["src/main/smithy"],
                          "plugins": {
                            "java-codegen": {
                              "namespace": "com.example.generated",
                              "modes": ["types"]
                            }
                          }
                        }
                        """);

        Path workDir = Files.createDirectories(projectRoot.resolve("build"));
        Path outDir = Files.createDirectories(workDir.resolve("generated-sources/smithy"));
        Path inputDir = projectRoot.resolve("src/main/smithy");

        CodeGenContext context = new CodeGenContext(
                /* model */ null,
                outDir,
                workDir,
                inputDir,
                /* redirectIO */ false,
                /* config */ null,
                /* test */ false);

        boolean generated = new SmithyCodeGenProvider().trigger(context);

        assertThat(generated).isTrue();
        // Model classes land under model/ for types-only mode.
        assertThat(outDir.resolve("com/example/generated/model/Menu.java")).exists();
        assertThat(outDir.resolve("com/example/generated/model/MenuItem.java")).exists();
        // No client or server stubs.
        assertThat(outDir.resolve("com/example/generated/client")).doesNotExist();
        assertThat(outDir.resolve("com/example/generated/service")).doesNotExist();
    }
}
