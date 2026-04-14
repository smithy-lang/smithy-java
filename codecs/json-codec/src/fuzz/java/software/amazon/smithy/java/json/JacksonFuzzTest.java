/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;

class JacksonFuzzTest extends CodecDeserializationFuzzTestBase {
    private static final JsonCodec CODEC =
            JsonCodec.builder().overrideSerdeProvider(new JacksonJsonSerdeProvider()).build();

    @Override
    protected Codec codecToFuzz() {
        return CODEC;
    }
}
