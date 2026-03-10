/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.utils.AbstractCodegenFileTest;

public class JavadocIntegrationTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
            JavadocIntegrationTest.class.getResource("javadoc-test.smithy"));

    @Override
    protected URL testFile() {
        return TEST_FILE;
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
                                    public String getShouldNotBeWrapped() {
                                """));
    }

    @Test
    void addsDeprecated() {
        var fileContents = getFileStringForClass("DeprecatedAnnotationInput");

        // Check that class header is added
        assertThat(fileContents, containsString("""
                /**
                 * @deprecated As of 1.3.
                 */
                @Deprecated(since = "1.3")
                @SmithyGenerated
                public final class DeprecatedAnnotationInput implements SerializableStruct {
                """));

        // Check that member headers match expected
        assertThat(fileContents, containsString("""
                    /**
                     * @deprecated As of 1.2.
                     */
                    @Deprecated(since = "1.2")
                    public String getDeprecatedMember() {
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
                    public String getDeprecatedWithDocs() {
                        return deprecatedWithDocs;
                    }
                """));
    }

    @Test
    void hasGeneratedAnnotationIfNoOtherDocs() {
        var fileContents = getFileStringForClass("SmithyGeneratedAnnotationInput");
        assertThat(fileContents, containsString("""
                @SmithyGenerated
                public final class SmithyGeneratedAnnotationInput implements SerializableStruct {
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
                public final class SinceInput implements SerializableStruct {
                """));
        assertThat(fileContents, containsString("""
                    /**
                     * @since 1.2
                     */
                    public String getSinceMember() {
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
                public final class ExternalDocumentationInput implements SerializableStruct {
                """));
        assertThat(fileContents, containsString("""
                    /**
                     * @see <a href="https://en.wikipedia.org/wiki/Puffin">Puffins are also neat</a>
                     */
                    public String getMemberWithExternalDocumentation() {
                """));
    }

    @Test
    void hasUnstableAnnotation() {
        var fileContents = getFileStringForClass("UnstableInput");
        assertThat(fileContents, containsString("""
                @SmithyUnstableApi
                @SmithyGenerated
                public final class UnstableInput implements SerializableStruct {
                """));
        assertThat(fileContents, containsString("""
                    @SmithyUnstableApi
                    public String getUnstableMember() {
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
                public final class RollupInput implements SerializableStruct {
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
                    public String getRollupMember() {
                """));
    }

    @Test
    void addsToEnumVariants() {
        var fileContents = getFileStringForClass("EnumWithDocs");
        // Sealed interface pattern - static fields (UPPER_SNAKE_CASE) instantiate inner classes (PascalCase + Type suffix)
        assertThat(
                fileContents,
                containsString(
                        """
                                    /**
                                     * @deprecated As of the past.
                                     */
                                    @Deprecated(since = "the past")
                                    EnumWithDocs DOCUMENTED = new DocumentedType();
                                    /**
                                     * General Docs
                                     */
                                    @SmithyUnstableApi
                                    EnumWithDocs ALSO_DOCUMENTED = new AlsoDocumentedType();
                                """));
    }

    @Test
    void skipsUnsupportedTagBlocks() {
        var fileContents = getFileStringForClass("UnsupportedTagsInput");
        assertThat(
                fileContents,
                containsString(
                        """
                                    /**
                                     * Documentation includes html tags that are not supported by Javadoc. These unsupported blocks should
                                     * be skipped.<pre>
                                     * This should be included.
                                     * </pre>
                                     * <pre>
                                     *     <code>CodeIsSupported</code>
                                     *
                                     * </pre>
                                     */
                                """));
    }

    @Test
    void addsBuilderSettersDocs() {
        var fileContents = getFileStringForClass("BuilderSettersInput");
        assertThat(
                fileContents,
                containsString("""
                                /**
                                 * Member with docs
                                 *
                                 * @return this builder.
                                 */
                                public Builder foo(String foo) {
                                    this.foo = foo;
                                    return this;
                                }
                        """));
    }

    @Test
    void addsBuilderSettersWithRequiredTraitDocs() {
        var fileContents = getFileStringForClass("BuilderSettersInput");
        assertThat(
                fileContents,
                containsString("""
                                /**
                                 * Required Field
                                 *
                                 * <p><strong>Required</strong>
                                 * @return this builder.
                                 */
                                public Builder required(String required) {
                                    this.required = Objects.requireNonNull(required, "required cannot be null");
                                    tracker.setMember($SCHEMA_REQUIRED);
                                    return this;
                                }
                        """));
    }

    @Test
    void addsBuilderSettersWithRecommendedTraitDocs() {
        var fileContents = getFileStringForClass("BuilderSettersInput");
        assertThat(
                fileContents,
                containsString("""
                                /**
                                 * Recommended Field
                                 *
                                 * <p><strong>Recommended</strong>
                                 * @return this builder.
                                 */
                                public Builder recommended(String recommended) {
                                    this.recommended = recommended;
                                    return this;
                                }
                        """));
    }
}
