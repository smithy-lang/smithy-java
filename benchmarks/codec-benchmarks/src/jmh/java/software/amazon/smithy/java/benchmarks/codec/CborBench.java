/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.codec;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.Codec;

@State(Scope.Benchmark)
public class CborBench extends CodecBench {

    @Override
    protected Codec createCodec() {
        return Rpcv2CborCodec.builder().build();
    }
}
