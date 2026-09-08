/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;

final class RuntimeCodegenPropertyValidationTest {
    @Test
    void propertyRequiresNativeSmithyProvider() {
        assumeTrue(RuntimeCodegenFeature.enabled("json"));
        assumeTrue(System.getProperty("smithy-java.json-provider") == null);

        assertThatThrownBy(() -> JsonCodec.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-Dsmithy-java.json-provider=smithy");
    }
}
