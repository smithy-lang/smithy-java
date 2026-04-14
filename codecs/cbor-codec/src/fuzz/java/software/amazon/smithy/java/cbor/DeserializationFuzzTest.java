/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;

class DeserializationFuzzTest {

    static class DefaultTest extends CodecDeserializationFuzzTestBase {

        @Override
        protected Codec codecToFuzz() {
            return Rpcv2CborCodec.builder().build();
        }
    }
}
