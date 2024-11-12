/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.runtime.cbor.JacksonCborCodec;
import software.amazon.smithy.java.runtime.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.example.model.AllFieldsOptional;
import software.amazon.smithy.java.runtime.example.model.AttributeUpdates;
import software.amazon.smithy.java.runtime.example.model.CodegenStruct;
import software.amazon.smithy.java.runtime.example.model.SendMessageRequest;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.utils.IoUtils;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(
    iterations = 2,
    time = 3,
    timeUnit = TimeUnit.SECONDS
)
@Measurement(
    iterations = 3,
    time = 3,
    timeUnit = TimeUnit.SECONDS
)
@Fork(1)
public class SmithyJavaTrials {
    private static final Map<String, Class<? extends ShapeBuilder<?>>> CASES = Map.ofEntries(
        Map.entry("all_fields_optional_0", AllFieldsOptional.Builder.class),
        Map.entry("all_fields_optional_1", AllFieldsOptional.Builder.class),
        Map.entry("all_fields_optional_3", AllFieldsOptional.Builder.class),
        Map.entry("all_fields_optional_5", AllFieldsOptional.Builder.class),
        Map.entry("all_fields_optional_6", AllFieldsOptional.Builder.class),
        Map.entry("attribute_updates_1", AttributeUpdates.Builder.class),
        Map.entry("attribute_updates_2", AttributeUpdates.Builder.class),
        Map.entry("attribute_updates_3", AttributeUpdates.Builder.class),
        Map.entry("struct_1", CodegenStruct.Builder.class),
        Map.entry("struct_2", CodegenStruct.Builder.class),
        Map.entry("struct_3", CodegenStruct.Builder.class),
        Map.entry("struct_4", CodegenStruct.Builder.class),
        Map.entry("send_message_request_1", SendMessageRequest.Builder.class)
    );

    public enum Protocol {
        RestJson(JsonCodec.builder().build()),
        RpcV2(Rpcv2CborCodec.builder().build()),
        JacksonCbor(new JacksonCborCodec()),
        ;

        private final Codec codec;

        Protocol(Codec codec) {
            this.codec = codec;
        }

        public <T extends SerializableStruct> ByteBuffer serialize(T obj) {
            return codec.serialize(obj);
        }
    }

    @Param(
        {
            "JacksonCbor",
            "RpcV2",
        }
    )
    private Protocol type;

    @Param(
        {
//            "all_fields_optional_0",
//            "all_fields_optional_1",
//            "all_fields_optional_3",
//            "all_fields_optional_5",
//            "all_fields_optional_6",
//            "attribute_updates_1",
//            "attribute_updates_2",
//            "attribute_updates_3",
//            "struct_1",
//            "struct_2",
//            "struct_3",
            "struct_4",
//            "send_message_request_1"
        }
    )
    private String testName;

    private ByteBuffer bytes;
    private SerializableStruct shape;
    private ShapeBuilder<?> cleanBuilder;

    @Setup
    public void setup() throws Exception {
        // The shape builder class being tested.
        var shapeBuilder = CASES.get(testName);

        // The bytes of the JSON for the test case. Each JSON document is serialized using no jsonName or
        // timestamp format trait.
        var preparationCodec = JsonCodec.builder().build();
        testName = testName + ".json";

        // TODO: for some reason, I can't access resources using relative paths with Class#getResource.
        var url = getClass().getClassLoader().getResource("software/amazon/smithy/java/runtime/" + testName);
        if (url == null) {
            throw new RuntimeException("Test case not found: " + testName);
        }
        var originalBytes = IoUtils.readUtf8Url(url).getBytes(StandardCharsets.UTF_8);

        var builderConstructor = shapeBuilder.getDeclaredConstructor();
        builderConstructor.setAccessible(true);

        // Deserialize it and reserialize it to ensure the bytes match the configured codec.
        var builder = builderConstructor.newInstance();

        // The shape to be serialized in the benchmark.
        shape = (SerializableStruct) preparationCodec.deserializeShape(originalBytes, builder);

        // These are the bytes that the CODEC under test expects (in case it ever is different from serialized tests).
        bytes = type.serialize(shape);

        // Where we'll later deserialize into during the deserialization benchmark.
        cleanBuilder = builderConstructor.newInstance();
    }

//    @Benchmark
    public Object serialize() {
        return type.codec.serialize(shape);
    }

    @Benchmark
    public Object deserialize() {
        return type.codec.deserializeShape(bytes, cleanBuilder);
    }

    public static final class Runner {
        public static void main(String[] args) throws Exception {
            run(Protocol.RpcV2, "struct_4");
        }

        private void runAll() throws Exception {
            for (var testName : Arrays.asList(
                new String[]{
                    "all_fields_optional_0",
                    "all_fields_optional_1",
                    "all_fields_optional_3",
                    "all_fields_optional_5",
                    "all_fields_optional_6",
                    "attribute_updates_1",
                    "attribute_updates_2",
                    "attribute_updates_3",
                    "struct_1",
                    "struct_2",
                    "struct_3",
                    "struct_4",
                    "send_message_request_1"
                }
            )) {
                run(Protocol.RpcV2, testName);
            }
        }

        private static void run(Protocol type, String testName) throws Exception {
            var trial = new SmithyJavaTrials();
            trial.type = Protocol.RpcV2;
            trial.testName = testName;
            trial.setup();
            trial.serialize();
            trial.deserialize();
        }
    }
}
