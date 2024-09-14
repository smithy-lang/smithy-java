/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.examples.dynamodb;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.aws.jsonprotocols.AwsJson1Protocol;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.AttributeValue;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.GetItem;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.GetItemInput;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.GetItemOutput;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.PutItem;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.PutItemInput;
import software.amazon.smithy.java.runtime.examples.dynamodb.model.PutItemOutput;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class DynamoDBSerde {

    private static final JsonCodec CODEC = JsonCodec.builder().build();

    @Benchmark
    public void putItem(PutItemState s, Blackhole bh) {
        var request = s.protocol.createRequest(s.operation, s.req, s.context, s.endpoint);
        bh.consume(request);
    }

    @Benchmark
    public void getItem(GetItemState s, Blackhole bh) throws Exception {
        var resp = fullResponse(s.testItem.utf8);
        var result = s.protocol.deserializeResponse(s.operation, s.context, s.operation.typeRegistry(), s.req, resp)
            .get();
        bh.consume(result);
    }

    @State(Scope.Benchmark)
    public static class PutItemState {
        @Param({"TINY", "SMALL", "HUGE"})
        private TestItem testItem;

        ApiOperation<PutItemInput, PutItemOutput> operation;
        URI endpoint;
        AwsJson1Protocol protocol;
        PutItemInput req;
        Context context = Context.create();

        @Setup
        public void setup() throws URISyntaxException {
            endpoint = new URI("https://dynamodb.us-east-1.amazonaws.com");
            operation = new PutItem();
            protocol = new AwsJson1Protocol(ShapeId.from("com.amazonaws.dynamodb#DynamoDB_20120810"));
            req = PutItemInput.builder().tableName("a").item(testItem.getValue()).build();
        }
    }

    @State(Scope.Benchmark)
    public static class GetItemState {
        @Param({"TINY", "SMALL", "HUGE"})
        private TestItemUnmarshalling testItem;

        ApiOperation<GetItemInput, GetItemOutput> operation;
        Context context = Context.create();
        URI endpoint;
        AwsJson1Protocol protocol;
        SmithyHttpRequest req;

        @Setup
        public void setup() throws URISyntaxException {
            // This isn't actually used, but needed for the protocol implementation.
            endpoint = new URI("https://dynamodb.us-east-1.amazonaws.com");
            req = SmithyHttpRequest.builder().method("POST").uri(endpoint).build();
            operation = new GetItem();
            protocol = new AwsJson1Protocol(ShapeId.from("com.amazonaws.dynamodb#DynamoDB_20120810"));
        }
    }

    public enum TestItem {
        TINY,
        SMALL,
        HUGE;

        private static final ItemFactory FACTORY = new ItemFactory();

        private Map<String, AttributeValue> av;

        static {
            TINY.av = FACTORY.tiny();
            SMALL.av = FACTORY.small();
            HUGE.av = FACTORY.huge();
        }

        public Map<String, AttributeValue> getValue() {
            return av;
        }
    }

    public enum TestItemUnmarshalling {
        TINY,
        SMALL,
        HUGE;

        private byte[] utf8;

        static {
            TINY.utf8 = toUtf8ByteArray(TestItem.TINY.av);
            SMALL.utf8 = toUtf8ByteArray(TestItem.SMALL.av);
            HUGE.utf8 = toUtf8ByteArray(TestItem.HUGE.av);
        }

        public byte[] utf8() {
            return utf8;
        }
    }

    private static byte[] toUtf8ByteArray(Map<String, AttributeValue> item) {
        return CODEC.serializeToString(GetItemOutput.builder().item(item).build()).getBytes(StandardCharsets.UTF_8);
    }

    private SmithyHttpResponse fullResponse(byte[] itemBytes) {
        return SmithyHttpResponse.builder()
            .statusCode(200)
            .body(DataStream.ofBytes(itemBytes))
            .build();
    }

    static final class ItemFactory {

        private static final String ALPHA = "abcdefghijklmnopqrstuvwxyz";
        private static final Random RNG = new Random();

        Map<String, AttributeValue> tiny() {
            return Map.of("stringAttr", av(randomS()));
        }

        Map<String, AttributeValue> small() {
            return Map.of(
                "stringAttr",
                av(randomS()),
                "binaryAttr",
                av(randomB()),
                "listAttr",
                av(List.of(av(randomS()), av(randomB()), av(randomS())))
            );
        }

        Map<String, AttributeValue> huge() {
            return Map.of(
                "hashKey",
                av(randomS()),
                "stringAttr",
                av(randomS()),
                "binaryAttr",
                av(randomB()),
                "listAttr",
                av(
                    List.of(
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomS()),
                        av(randomB()),
                        av(Collections.singletonList(av(randomS()))),
                        av(Map.of("attrOne", av(randomS()))),
                        av(
                            Arrays.asList(
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomB()),
                                (av(randomS())),
                                av(Map.of("attrOne", av(randomS())))
                            )
                        )
                    )
                ),
                "mapAttr",
                av(
                    Map.of(
                        "attrOne",
                        av(randomS()),
                        "attrTwo",
                        av(randomB()),
                        "attrThree",
                        av(
                            List.of(
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(randomS()),
                                av(
                                    Map.of(
                                        "attrOne",
                                        av(randomS()),
                                        "attrTwo",
                                        av(randomB()),
                                        "attrThree",
                                        av(
                                            List.of(
                                                av(randomS()),
                                                av(randomS()),
                                                av(randomS()),
                                                av(randomS())
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }

        private AttributeValue av(String val) {
            return AttributeValue.builder().s(val).build();
        }

        private AttributeValue av(ByteBuffer val) {
            return AttributeValue.builder().b(val).build();
        }

        private AttributeValue av(List<AttributeValue> val) {
            return AttributeValue.builder().l(val).build();
        }

        private AttributeValue av(Map<String, AttributeValue> val) {
            return AttributeValue.builder().m(val).build();
        }

        private String randomS(int len) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; ++i) {
                sb.append(ALPHA.charAt(RNG.nextInt(ALPHA.length())));
            }
            return sb.toString();
        }

        private String randomS() {
            return randomS(16);
        }

        private ByteBuffer randomB(int len) {
            byte[] b = new byte[len];
            RNG.nextBytes(b);
            return ByteBuffer.wrap(b);
        }

        private ByteBuffer randomB() {
            return randomB(16);
        }
    }
}
