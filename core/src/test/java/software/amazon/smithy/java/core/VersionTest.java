/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class VersionTest {
    @Test
    void versionMatchesCurrent() {
        var systemProperty = System.getProperty("smithy.java.version");
        assertEquals(systemProperty, Version.VERSION);
    }
}
