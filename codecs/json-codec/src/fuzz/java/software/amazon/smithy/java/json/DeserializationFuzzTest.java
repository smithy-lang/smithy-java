/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.List;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Fuzz tests for JsonCodec with both Jackson and smithy-native providers.
 *
 * <p>Each fuzz input is tested against both providers within the same time budget.
 */
class DeserializationFuzzTest {

    static class DefaultTest extends CodecDeserializationFuzzTestBase {
        private static final JsonCodec JACKSON_CODEC =
                JsonCodec.builder().overrideSerdeProvider(new JacksonJsonSerdeProvider()).build();
        private static final JsonCodec SMITHY_CODEC =
                JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();

        @Override
        protected List<Codec> codecsToFuzz() {
            return List.of(JACKSON_CODEC, SMITHY_CODEC);
        }
    }
}
