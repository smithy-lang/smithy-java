/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CodegenFallbackTest {

    @Test
    void codegenNotActiveOnPreJdk25() {
        assertTrue(Runtime.version().feature() < 25,
                "This test must run on JDK < 25 to verify fallback");
        try {
            Class.forName("software.amazon.smithy.java.json.codegen.ClassFileSpecializedJsonSerde");
            throw new AssertionError("ClassFileSpecializedJsonSerde should not be loadable on JDK < 25");
        } catch (ClassNotFoundException | LinkageError expected) {
            // ClassFile API classes don't exist on JDK < 25, so loading this class should fail
        }
    }
}
