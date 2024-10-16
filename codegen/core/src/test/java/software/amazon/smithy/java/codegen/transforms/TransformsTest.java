/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TransformsTest {
    static List<Arguments> semverSupplier() {
        return List.of(
            Arguments.of("1.0.0", "1.0.0", 0),
            Arguments.of("1.0.0", "1.0.1", -1),
            Arguments.of("1.1.0", "1.1.1", -1),
            Arguments.of("1.1.0", "1.0.1", 1),
            Arguments.of("1.1.1", "1.1.1.1", -1),
            Arguments.of("1.0.0.1", "1.0.0", 1),
            Arguments.of("1.0.0", "1.0", 0),
            Arguments.of("20.20.0.1", "20.20.1.0", -1),
            Arguments.of("20.20.1.0", "20.20.1.0-PATCH", -1)
        );
    }

    @ParameterizedTest
    @MethodSource("semverSupplier")
    void testSemverComparison(String semver1, String semver2, int expected) {
        var result = RemoveDeprecatedShapesTransformer.compareSemVer(semver1, semver2);
        switch (expected) {
            case 0 -> assertEquals(result, 0);
            case -1 -> assertTrue(result < 0);
            case 1 -> assertTrue(result > 0);
        }
    }
}
