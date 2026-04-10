/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Fuzz tests for JsonCodec with various configurations.
 *
 * <p>Uses a single fuzz test that alternates between Jackson and smithy-native providers
 * on each {@code codecToFuzz()} call. Since the base class calls {@code codecToFuzz()} once
 * per shape builder, both providers get exercised within the same fuzz time budget.
 */
class DeserializationFuzzTest {

    static class DefaultTest extends CodecDeserializationFuzzTestBase {
        private static final JsonCodec JACKSON_CODEC =
                JsonCodec.builder().overrideSerdeProvider(new JacksonJsonSerdeProvider()).build();
        private static final JsonCodec SMITHY_CODEC =
                JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();

        private boolean useSmithy;

        @Override
        protected Codec codecToFuzz() {
            // Alternate between providers on each call.
            // The base class calls this once per shape builder in the fuzz loop,
            // so both providers get fuzzed with every input.
            useSmithy = !useSmithy;
            return useSmithy ? SMITHY_CODEC : JACKSON_CODEC;
        }
    }

}
