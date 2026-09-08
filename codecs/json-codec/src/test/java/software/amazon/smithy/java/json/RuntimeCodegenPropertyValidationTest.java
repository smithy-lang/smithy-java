/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Validates property-driven runtime codegen activation against the provider the
 * {@code smithy-java.json-provider} property selects.
 *
 * <p>The {@code runtimeCodegenPropertyValidationTest} task runs this class with the default provider and
 * {@code runtimeCodegenJacksonPropertyValidationTest} runs it with Jackson selected; each test guards on the
 * provider configuration it needs.
 */
final class RuntimeCodegenPropertyValidationTest {

    @Test
    void propertyActivatesCodegenOverDefaultProvider() {
        assumeTrue(RuntimeCodegenFeature.enabled("json"));
        assumeTrue(System.getProperty("smithy-java.json-provider") == null);
        var provider = JsonSettings.builder().build().provider();
        assertThat(provider).isInstanceOf(CodegenJsonSerdeProvider.class);
        assertThat(((CodegenJsonSerdeProvider) provider).delegate()).isInstanceOf(SmithyJsonSerdeProvider.class);
    }

    @Test
    void propertyRequiresNativeSmithyProvider() {
        assumeTrue(RuntimeCodegenFeature.enabled("json"));
        assumeTrue("jackson".equals(System.getProperty("smithy-java.json-provider")));
        assertThatThrownBy(() -> JsonCodec.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-Dsmithy-java.json-provider=smithy");
    }
}
