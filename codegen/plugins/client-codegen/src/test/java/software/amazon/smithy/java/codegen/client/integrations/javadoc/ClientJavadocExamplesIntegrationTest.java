/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.javadoc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.client.utils.AbstractCodegenFileTest;

public class ClientJavadocExamplesIntegrationTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
            ClientJavadocExamplesIntegrationTest.class.getResource("javadoc-examples.smithy"));

    @Override
    protected URL testFile() {
        return TEST_FILE;
    }

    @Test
    void includesExamples() {
        var fileContents = getFileStringForClass("client/TestServiceClient");
        var expected = """
                     * <h4>Examples</h4>
                     * <h5>Basic Example</h5>
                     * <pre>
                     * {@code
                     * var input = ExamplesOperationInput.builder()
                     *                 .foo("foo")
                     *                 .build();
                     *
                     * var result = client.examplesOperation(input);
                     * result.equals(ExamplesOperationOutput.builder()
                     *                   .bar("bar")
                     *                   .build());
                     * }
                     * </pre>
                     *
                     * <h5>Error Example</h5>
                     * <pre>
                     * {@code
                     * var input = ExamplesOperationInput.builder()
                     *                 .foo("bar")
                     *                 .build();
                     *
                     * try {
                     *     client.examplesOperation(input);
                     * } catch (ExampleError e) {
                     *     e.equals(ExampleError.builder()
                     *                  .message("bar")
                     *                  .build());
                     * }
                     * }
                     * </pre>
                """;
        assertThat(fileContents, containsString(expected));
    }
}
