/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.utils.AbstractCodegenFileTest;
import software.amazon.smithy.model.node.ObjectNode;

public class RelativeDeprecationFilteringTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        RelativeDeprecationFilteringTest.class.getResource("relative-deprecated-test.smithy")
    );

    @Override
    protected URL testFile() {
        return TEST_FILE;
    }

    protected ObjectNode settings() {
        return ObjectNode.builder()
            .withMember("service", "smithy.java.codegen#TestService")
            .withMember("namespace", "test.smithy.codegen")
            .withMember("relativeDate", "1990-01-01")
            .withMember("relativeVersion", "1.1.0")
            .build();
    }

    @Test
    void expectedUnfilteredDateExist() {
        var fileStr = getFileStringForClass("NotYetDeprecatedDate");
        System.out.println(fileStr);
        var expected = "public final class NotYetDeprecatedDate implements ApiOperation";
        assertTrue(fileStr.contains(expected));
    }

    @Test
    void expectedFilteredDateDoesNotExist() {
        var fileStringOptional = manifest.getFileString(
            Paths.get("/test/smithy/codegen/model/DeprecatedOperationDate.java")
        );
        assertTrue(fileStringOptional.isEmpty());
    }

    @Test
    void expectedUnfilteredVersionExist() {
        var fileStr = getFileStringForClass("NotYetDeprecatedVersion");
        var expected = "public final class NotYetDeprecatedVersion implements ApiOperation";
        assertTrue(fileStr.contains(expected));
    }

    @Test
    void expectedFilteredVersionDoesNotExist() {
        var fileStringOptional = manifest.getFileString(
            Paths.get("/test/smithy/codegen/model/DeprecatedOperationVersion.java")
        );
        assertTrue(fileStringOptional.isEmpty());
    }
}
