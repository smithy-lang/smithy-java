/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.core.schema.PreludeSchemas;

@State(Scope.Thread)
public class CborBigNumberReadBenchmark {

    @Param({
            "123456789012345678901",
            "-123456789012345678901",
            "7x100",
            "7x1000",
    })
    public String integerText;

    private Rpcv2CborCodec codec;
    private byte[] bigIntegerPayload;
    private byte[] bigDecimalPayload;

    @Setup
    public void setup() {
        codec = Rpcv2CborCodec.builder().build();
        BigInteger value = new BigInteger(integerText.startsWith("7x")
                ? "7".repeat(Integer.parseInt(integerText.substring(2)))
                : integerText);
        bigIntegerPayload = bytes(codec.serialize(s -> s.writeBigInteger(PreludeSchemas.BIG_INTEGER, value)));
        BigDecimal decimal = new BigDecimal(value, 9);
        bigDecimalPayload = bytes(codec.serialize(s -> s.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, decimal)));
        if (!codec.createDeserializer(bigIntegerPayload).readBigInteger(PreludeSchemas.BIG_INTEGER).equals(value)) {
            throw new IllegalStateException("round trip failed");
        }
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    @Benchmark
    public BigInteger readBigInteger() {
        return codec.createDeserializer(bigIntegerPayload).readBigInteger(PreludeSchemas.BIG_INTEGER);
    }

    @Benchmark
    public BigDecimal readBigDecimal() {
        return codec.createDeserializer(bigDecimalPayload).readBigDecimal(PreludeSchemas.BIG_DECIMAL);
    }
}
