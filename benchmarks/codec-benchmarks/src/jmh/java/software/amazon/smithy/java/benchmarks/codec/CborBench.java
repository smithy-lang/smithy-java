/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.document.Document;

@State(Scope.Benchmark)
public class CborBench extends CodecBench {

    @Override
    protected Codec createCodec() {
        return Rpcv2CborCodec.builder().build();
    }

    @Override
    protected byte[] reverseFieldOrder(byte[] bytes) {
        return reverseCborFieldOrder(bytes, codec);
    }

    @Benchmark
    public Object deserializeReversed() {
        return codec.deserializeShape(reversedBytes, builderSupplier.get());
    }

    /**
     * Reverses the order of top-level CBOR map entries by round-tripping through Document.
     * Only used at setup time.
     */
    private static byte[] reverseCborFieldOrder(byte[] cbor, Codec codec) {
        try {
            var deserializer = codec.createDeserializer(cbor);
            Document doc = deserializer.readDocument();
            if (doc == null) {
                return cbor;
            }

            Map<String, Document> original = doc.asStringMap();
            List<Map.Entry<String, Document>> entries = new ArrayList<>(original.entrySet());
            Collections.reverse(entries);
            Map<String, Document> reversed = new LinkedHashMap<>();
            for (var entry : entries) {
                reversed.put(entry.getKey(), entry.getValue());
            }

            Document reversedDoc = Document.of(reversed);
            ByteBuffer buf = codec.serialize(reversedDoc);
            byte[] result = new byte[buf.remaining()];
            buf.get(result);
            return result;
        } catch (Exception e) {
            // Document round-trip may not preserve all CBOR types faithfully;
            // fall back to original order (deserializeReversed == deserialize for this case).
            return cbor;
        }
    }
}
