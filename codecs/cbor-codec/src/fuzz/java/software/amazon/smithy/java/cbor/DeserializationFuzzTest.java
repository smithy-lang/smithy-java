/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.List;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;

/**
 * Fuzz tests for Rpcv2CborCodec with various configurations.
 */
class DeserializationFuzzTest {

    static class DefaultTest extends CodecDeserializationFuzzTestBase {

        @Override
        protected List<Codec> codecsToFuzz() {
            return List.of(Rpcv2CborCodec.builder().build());
        }
    }

}
