/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.smithy.java.codegen.test.PluginTestRunner.addTestCasesFromUrl;
import static software.amazon.smithy.java.codegen.test.PluginTestRunner.findGotContent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.PluginTestRunner.TestCase;

public class TypesCodegenPluginTest {

    @ParameterizedTest(name = "[{index}] => {0}")
    @MethodSource("testCases")
    public void runTestCase(TestCase test) {
        test.builder().build();
        var got = test.manifests().stream().flatMap(x -> x.getFiles().stream()).collect(Collectors.toSet());
        for (var expected : test.expectedToContents().keySet()) {
            var found = findExpected(expected, got);
            assertNotNull(found, "Expected to find " + expected + " in the manifest");
            var contents = findGotContent(found, test);
            assertTrue(contents.isPresent());
            assertEquals(test.expectedToContents().get(expected), contents.get());
        }
    }

    // Uncomment this test to render the java files when we there are changes to the codegen logic.
    @ParameterizedTest(name = "[{index}] => {0}")
    @MethodSource("testCases")
    public void renderExpected(TestCase test) throws IOException {
        test.builder().build();
        var got = test.manifests().stream().flatMap(x -> x.getFiles().stream()).collect(Collectors.toSet());
        for (var expected : test.expectedToContents().keySet()) {
            var found = findExpected(expected, got);
            assertNotNull(found);
            var contents = findGotContent(found, test);
            var expectedFile = new File("/tmp/rendered/" + test + "/expected" + expected);
            expectedFile.getParentFile().mkdirs();
            Files.write(expectedFile.toPath(), contents.get().getBytes(StandardCharsets.UTF_8));
        }
    }

    private Path findExpected(String expected, Set<Path> manifestFiles) {
        return manifestFiles.stream().filter(path -> path.toString().contains(expected)).findFirst().orElse(null);
    }

    public static Collection<TestCase> testCases() {
        return addTestCasesFromUrl(TypesCodegenPluginTest.class.getResource("test-cases"));
    }
}
