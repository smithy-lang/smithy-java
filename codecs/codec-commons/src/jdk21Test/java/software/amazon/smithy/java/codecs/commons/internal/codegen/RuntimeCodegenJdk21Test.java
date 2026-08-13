/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class RuntimeCodegenJdk21Test {
    @Test
    void enabledGateFallsBackWithoutLoadingClassFileBackend() {
        assertThat(Runtime.version().feature()).isEqualTo(21);
        System.setProperty("smithy-java.runtime-codegen", "json,cbor");
        try {
            assertThat(RuntimeCodegenFeature.enabled("json")).isFalse();
            assertThat(RuntimeCodegenFeature.enabled("cbor")).isFalse();
        } finally {
            System.clearProperty("smithy-java.runtime-codegen");
        }
    }
}
