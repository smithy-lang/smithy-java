/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonCodec;

/**
 * Baseline JMH benchmark for the AWS event-stream decode hot path.
 *
 * <p>Each invocation drives {@link AwsEventShapeDecoder#decode} (or {@code decodeInitialEvent}) for one
 * pre-built {@link AwsEventFrame}. Frames are constructed once in {@code @Setup} so the loop measures only
 * decode work.
 *
 * <p>Archetypes exercise the per-frame schema work the schema-extension caching is meant to remove — in
 * particular {@code EventStreamDeserializer.readStruct} re-walking all members and calling {@code hasTrait}
 * for the {@code @eventHeader}/{@code @eventPayload} partition on every frame:
 *
 * <ul>
 *   <li>{@code headersOnly} — header-only event; every member resolved from headers.</li>
 *   <li>{@code structure} — body-only event; falls through to the codec struct read.</li>
 *   <li>{@code bodyAndHeader} — mixed header + body partition.</li>
 *   <li>{@code stringPayload} — {@code @eventPayload} string member.</li>
 *   <li>{@code initialResponse} — the initial-response path ({@code decodeInitialEvent}).</li>
 * </ul>
 *
 * <p>Run with {@code -Pjmh.profilers=gc} to capture {@code gc.alloc.rate.norm}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class EventDecodeBenchmark {

    @Param({"headersOnly", "structure", "bodyAndHeader", "stringPayload", "initialResponse"})
    public String archetype;

    private AwsEventShapeDecoder<?, ?> decoder;
    private AwsEventFrame frame;
    private boolean initial;

    @Setup
    public void setup() {
        var operation = TestOperation.instance();
        decoder = createDecoder(operation);
        frame = buildFrame(archetype);
        initial = archetype.equals("initialResponse");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AwsEventShapeDecoder<?, ?> createDecoder(ApiOperation<?, ?> operation) {
        return new AwsEventShapeDecoder<>(
                InitialEventType.INITIAL_RESPONSE,
                (Supplier<ShapeBuilder<SerializableStruct>>) (Supplier) () -> operation.outputBuilder(),
                (Supplier) operation.outputEventBuilderSupplier(),
                operation.outputStreamMember(),
                JsonCodec.builder().build());
    }

    private static AwsEventFrame buildFrame(String archetype) {
        return switch (archetype) {
            case "headersOnly" -> frame(HeadersBuilder.forEvent()
                    .contentType("text/json")
                    .eventType("headersOnlyMember")
                    .put("sequenceNum", 123)
                    .build(), "{}");
            case "structure" -> frame(HeadersBuilder.forEvent()
                    .contentType("text/json")
                    .eventType("structureMember")
                    .build(), "{\"foo\":\"memberFooValue\"}");
            case "bodyAndHeader" -> frame(HeadersBuilder.forEvent()
                    .contentType("text/json")
                    .eventType("bodyAndHeaderMember")
                    .put("intMember", 123)
                    .build(), "{\"stringMember\":\"Hello world!\"}");
            case "stringPayload" -> frame(HeadersBuilder.forEvent()
                    .contentType("text/json")
                    .eventType("stringMember")
                    .build(), "\"hello world!\"");
            case "initialResponse" -> frame(HeadersBuilder.forEvent()
                    .eventType("initial-response")
                    .contentType("text/json")
                    .put("intMemberHeader", 123)
                    .build(), "{\"stringMember\":\"Hello World!\"}");
            default -> throw new IllegalArgumentException("Unknown archetype: " + archetype);
        };
    }

    private static AwsEventFrame frame(
            Map<String, HeaderValue> headers,
            String payload
    ) {
        return new AwsEventFrame(new Message(headers, payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Benchmark
    public void decode(Blackhole bh) {
        if (initial) {
            bh.consume(decoder.decodeInitialEvent(frame, null));
        } else {
            bh.consume(decoder.decode(frame));
        }
    }
}
