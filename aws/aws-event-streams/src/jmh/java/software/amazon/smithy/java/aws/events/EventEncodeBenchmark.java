/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
import software.amazon.smithy.java.aws.events.model.BlobEvent;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestEventStream;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.aws.events.model.TestOperationInput;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.json.JsonCodec;

/**
 * Baseline JMH benchmark for the AWS event-stream encode hot path.
 *
 * <p>Each invocation drives {@link AwsEventShapeEncoder#encode(SerializableStruct)} for one event
 * archetype. The archetypes are chosen to exercise the per-frame schema work that the schema-extension
 * caching is meant to remove:
 *
 * <ul>
 *   <li>{@code headersOnly} — all members {@code @eventHeader}; exercises the headers filter pass and
 *       {@code hasPayloadMembers} returning false.</li>
 *   <li>{@code structure} — a plain body-only struct; exercises {@code hasPayloadMembers} /
 *       {@code hasEventPayloadMember} and the payload-member filter pass.</li>
 *   <li>{@code bodyAndHeader} — mixed; exercises both the header and payload filter passes in one frame.</li>
 *   <li>{@code stringPayload} / {@code blobPayload} — {@code @eventPayload} members; exercise
 *       {@code hasEventPayloadMember} and the event-payload filter pass.</li>
 *   <li>{@code initialRequest} — the whole initial-request struct minus the streaming member; exercises
 *       {@code isInitialRequest} and the stream-member exclusion filter.</li>
 * </ul>
 *
 * <p>Run with {@code -Pjmh.profilers=gc} to capture {@code gc.alloc.rate.norm} (bytes allocated per op) —
 * the current code allocates intermediate filtered-member wrappers and header sets per frame, so allocation
 * is expected to be the most telling baseline number.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class EventEncodeBenchmark {

    @Param({"headersOnly", "structure", "bodyAndHeader", "stringPayload", "blobPayload", "initialRequest"})
    public String archetype;

    private AwsEventShapeEncoder encoder;
    private SerializableStruct event;

    @Setup
    public void setup() {
        var operation = TestOperation.instance();
        var codec = JsonCodec.builder().build();
        // Encoder is built once per trial — mirrors real usage where one encoder serves a whole stream.
        encoder = new AwsEventShapeEncoder(
                InitialEventType.INITIAL_REQUEST,
                operation.outputStreamMember(),
                codec,
                "text/json",
                false,
                e -> new EventStreamingException("InternalServerException", "Internal Server Error"));
        event = buildEvent(archetype);
    }

    private static SerializableStruct buildEvent(String archetype) {
        return switch (archetype) {
            case "headersOnly" -> TestEventStream.builder()
                    .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                    .build();
            case "structure" -> TestEventStream.builder()
                    .structureMember(StructureEvent.builder().foo("memberFooValue").build())
                    .build();
            case "bodyAndHeader" -> TestEventStream.builder()
                    .bodyAndHeaderMember(BodyAndHeaderEvent.builder()
                            .intMember(123)
                            .stringMember("Hello world!")
                            .build())
                    .build();
            case "stringPayload" -> TestEventStream.builder()
                    .stringMember(StringEvent.builder().payload("hello world!").build())
                    .build();
            case "blobPayload" -> TestEventStream.builder()
                    .blobMember(BlobEvent.builder()
                            .payload(ByteBuffer.wrap("hello world!".getBytes(StandardCharsets.UTF_8)))
                            .build())
                    .build();
            case "initialRequest" -> TestOperationInput.builder()
                    .headerString("headerValue")
                    .inputStringMember("inputStringValue")
                    .build();
            default -> throw new IllegalArgumentException("Unknown archetype: " + archetype);
        };
    }

    @Benchmark
    public void encode(Blackhole bh) {
        bh.consume(encoder.encode(event));
    }
}
