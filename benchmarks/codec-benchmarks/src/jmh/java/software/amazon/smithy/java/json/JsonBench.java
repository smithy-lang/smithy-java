/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static tools.jackson.core.JsonToken.PROPERTY_NAME;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.benchmarks.codec.CodecBench;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

@State(Scope.Benchmark)
public class JsonBench extends CodecBench {

    public enum Provider {
        jackson,
        smithy,
    }

    @Param
    private Provider provider;

    @Override
    protected Codec createCodec() {
        JsonSerdeProvider serdeProvider = switch (provider) {
            case jackson -> new JacksonJsonSerdeProvider();
            case smithy -> new SmithyJsonSerdeProvider();
        };
        return JsonCodec.builder()
                .overrideSerdeProvider(serdeProvider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();
    }

    @Override
    protected byte[] reverseFieldOrder(byte[] bytes) {
        return reverseJsonFieldOrder(bytes);
    }

    @Benchmark
    public Object deserializeReversed() {
        return codec.deserializeShape(reversedBytes, builderSupplier.get());
    }

    /**
     * Reverses the order of top-level JSON object fields using Jackson streaming.
     * Only used at setup time.
     */
    private static byte[] reverseJsonFieldOrder(byte[] json) {
        var factory = JsonFactory.builder().build();

        // Parse top-level fields and capture each value as raw bytes
        List<Map.Entry<String, byte[]>> fields = new ArrayList<>();
        try (var parser = factory.createParser(ObjectReadContext.empty(), json)) {
            parser.nextToken(); // START_OBJECT
            while (parser.nextToken() == PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken(); // advance to value
                var baos = new ByteArrayOutputStream();
                try (var gen = factory.createGenerator(ObjectWriteContext.empty(), baos)) {
                    gen.copyCurrentStructure(parser);
                }
                fields.add(new AbstractMap.SimpleEntry<>(name, baos.toByteArray()));
            }
        }

        // Re-emit in reverse order
        Collections.reverse(fields);
        var out = new ByteArrayOutputStream();
        try (var gen = factory.createGenerator(ObjectWriteContext.empty(), out)) {
            gen.writeStartObject();
            for (var field : fields) {
                gen.writeName(field.getKey());
                try (var p = factory.createParser(ObjectReadContext.empty(), field.getValue())) {
                    p.nextToken();
                    gen.copyCurrentStructure(p);
                }
            }
            gen.writeEndObject();
        }
        return out.toByteArray();
    }
}
