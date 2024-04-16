/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

public class JavadocIntegrationTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        JavadocIntegrationTest.class.getResource("javadoc-test.smithy")
    );
    private final MockManifest manifest = new MockManifest();

    @BeforeEach
    public void setup() {
        var model = Model.assembler()
            .addImport(TEST_FILE)
            .assemble()
            .unwrap();
        var context = PluginContext.builder()
            .fileManifest(manifest)
            .settings(
                ObjectNode.builder()
                    .withMember("service", "smithy.java.codegen.integrations.javadoc#TestService")
                    .withMember("namespace", "test.smithy.traitcodegen")
                    .build()
            )
            .model(model)
            .build();
        SmithyBuildPlugin plugin = new JavaCodegenPlugin();
        plugin.execute(context);

        assertFalse(manifest.getFiles().isEmpty());
    }

    @Test
    void longDocstringsWrapped() {
        var fileContents = getFileStringForClass("DocStringWrappingInput");
        assertThat(fileContents, containsString("""
                /**
                 * This is a long long docstring that should be wrapped. Lorem ipsum dolor sit amet, consectetur
                 * adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
                 */
            """));
    }

    @Test
    void htmlFormattedTextNotWrapped() {
        var fileContents = getFileStringForClass("DocStringWrappingInput");
        assertThat(
            fileContents,
            containsString(
                """
                        /**
                         * Documentation includes preformatted text that should not be messed with.
                         * For example:<pre>
                         * Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
                         * </pre>
                         * <ul>
                         *     <li> Lorem ipsum dolor sit amet, consectetur adipiscing elit Lorem ipsum dolor sit amet, consectetur adipiscing elit </li>
                         *     <li> Lorem ipsum dolor sit amet, consectetur adipiscing elit Lorem ipsum dolor sit amet, consectetur adipiscing elit </li>
                         * </ul>
                         */
                        public String shouldNotBeWrapped() {
                    """
            )
        );
    }

    @Test
    void addsDeprecated() {
        var fileContents = getFileStringForClass("DeprecatedInput");

        // Check that class header is added
        assertThat(fileContents, containsString("""
            /**
             * @deprecated As of 1.3.
             */
            @Deprecated(since = "1.3")
            @SmithyGenerated
            public final class DeprecatedInput implements SerializableShape {
            """));

        // Check that member headers match expected
        assertThat(fileContents, containsString("""
                /**
                 * @deprecated As of 1.2.
                 */
                @Deprecated(since = "1.2")
                public String deprecatedMember() {
                    return deprecatedMember;
                }
            """));
        assertThat(fileContents, containsString("""
                /**
                 * @deprecated
                 */
                @Deprecated
            """));
        assertThat(fileContents, containsString("""
                /**
                 * Has docs in addition to deprecated
                 *
                 * @deprecated
                 */
                @Deprecated
                public String deprecatedWithDocs() {
                    return deprecatedWithDocs;
                }
            """));
    }

    @Test
    void hasGeneratedAnnotationIfNoOtherDocs() {
        var fileContents = getFileStringForClass("SmithyGeneratedInput");
        assertThat(fileContents, containsString("""
            @SmithyGenerated
            public final class SmithyGeneratedInput implements SerializableShape {
            """));
    }

    @Test
    void hasSince() {
        var fileContents = getFileStringForClass("SinceInput");
        assertThat(fileContents, containsString("""
            /**
             * @since 4.5
             */
            @SmithyGenerated
            public final class SinceInput implements SerializableShape {
            """));
        assertThat(fileContents, containsString("""
                /**
                 * @since 1.2
                 */
                public String sinceMember() {
            """));
    }

    @Test
    void hasExternalDocumentation() {
        var fileContents = getFileStringForClass("ExternalDocumentationInput");
        assertThat(fileContents, containsString("""
            /**
             * @see <a href="https://en.wikipedia.org/wiki/Auk">Auks are cool birds</a>
             * @see <a href="https://example.com">Example</a>
             */
            @SmithyGenerated
            public final class ExternalDocumentationInput implements SerializableShape {
            """));
        assertThat(fileContents, containsString("""
                /**
                 * @see <a href="https://en.wikipedia.org/wiki/Puffin">Puffins are also neat</a>
                 */
                public String memberWithExternalDocumentation() {
            """));
    }

    @Test
    void hasUnstableAnnotation() {
        var fileContents = getFileStringForClass("UnstableInput");
        assertThat(fileContents, containsString("""
            @SmithyUnstableApi
            @SmithyGenerated
            public final class UnstableInput implements SerializableShape {
            """));
        assertThat(fileContents, containsString("""
                @SmithyUnstableApi
                public String unstableMember() {
                    return unstableMember;
                }
            """));
    }

    @Test
    void composeCorrectly() {
        var fileContents = getFileStringForClass("RollupInput");
        assertThat(fileContents, containsString("""
            /**
             * This structure applies all documentation traits
             *
             * @see <a href="https://en.wikipedia.org/wiki/Puffin">Puffins are still cool</a>
             * @since 4.5
             * @deprecated As of sometime.
             */
            @SmithyUnstableApi
            @Deprecated(since = "sometime")
            @SmithyGenerated
            public final class RollupInput implements SerializableShape {
            """));
        assertThat(fileContents, containsString("""
                /**
                 * This member applies all documentation traits
                 *
                 * @see <a href="https://en.wikipedia.org/wiki/Puffin">Puffins are still cool</a>
                 * @since 4.5
                 * @deprecated As of sometime.
                 */
                @SmithyUnstableApi
                @Deprecated(since = "sometime")
                public String rollupMember() {
            """));
    }

    private String getFileStringForClass(String className) {
        var fileStringOptional = manifest.getFileString(
            Paths.get(String.format("/test/smithy/traitcodegen/model/%s.java", className))
        );
        assertTrue(fileStringOptional.isPresent());
        return fileStringOptional.get();
    }
}
