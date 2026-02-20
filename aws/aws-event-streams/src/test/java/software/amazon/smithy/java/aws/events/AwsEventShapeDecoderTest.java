/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestEventStream;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.aws.events.model.TestOperationOutput;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.JsonCodec;

class AwsEventShapeDecoderTest {

    @Test
    public void testDecodeInitialResponse() {
        // Arrange
        var headers = new AwsEventShapeEncoderTest.HeadersBuilder()
                .eventType("initial-response")
                .contentType("text/json")
                .put("intMemberHeader", 123)
                .build();
        var message = new Message(headers, "{\"stringMember\":\"Hello World!\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder().decodeInitialEvent(frame, null);

        // Assert
        assertInstanceOf(TestOperationOutput.class, struct);
        TestOperationOutput expected = TestOperationOutput.builder()
                .intMemberHeader(123)
                .stringMember("Hello World!")
                .build();
        assertEquals(expected, struct);
    }

    @Test
    public void testDecodeHeadersOnlyMember() {
        // Arrange
        var headers = new AwsEventShapeEncoderTest.HeadersBuilder()
                .contentType("text/json")
                .eventType("headersOnlyMember")
                .put("sequenceNum", 123)
                .build();
        var message = new Message(headers, "{}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder().decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertEquals(TestEventStream.Type.headersOnlyMember, actual.type());
        var expected = TestEventStream.builder()
                .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeStructureMember() {
        // Arrange
        var headers = new AwsEventShapeEncoderTest.HeadersBuilder()
                .contentType("text/json")
                .eventType("structureMember")
                .build();
        var message = new Message(headers, "{\"foo\":\"memberFooValue\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder().decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertEquals(TestEventStream.Type.structureMember, actual.type());
        var expected = TestEventStream.builder()
                .structureMember(StructureEvent.builder().foo("memberFooValue").build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeBodyAndHeaderMember() {
        // Arrange
        var headers = new AwsEventShapeEncoderTest.HeadersBuilder()
                .contentType("text/json")
                .eventType("bodyAndHeaderMember")
                .put("intMember", 123)
                .build();
        var message = new Message(headers, "{\"stringMember\":\"Hello world!\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder().decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertEquals(TestEventStream.Type.bodyAndHeaderMember, actual.type());
        var expected = TestEventStream.builder()
                .bodyAndHeaderMember(BodyAndHeaderEvent.builder()
                        .intMember(123)
                        .stringMember("Hello world!")
                        .build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeStringMember() {
        // Arrange
        var headers = new AwsEventShapeEncoderTest.HeadersBuilder()
                .contentType("text/json")
                .eventType("stringMember")
                .build();;
        var message = new Message(headers, "\"hello world!\"".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder().decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertEquals(TestEventStream.Type.stringMember, actual.type());
        var expected = TestEventStream.builder()
                .stringMember(StringEvent.builder().payload("hello world!").build())
                .build();
        assertEquals(expected, actual);
    }

    static AwsEventShapeDecoder<?, ?> createDecoder() {
        return new AwsEventShapeDecoder<>(InitialEventType.INITIAL_RESPONSE,
                () -> TestOperation.instance().outputBuilder(), // output builder
                TestOperation.instance().outputEventBuilderSupplier(),
                TestOperation.instance().outputStreamMember(),
                createJsonCodec());
    }

    static Codec createJsonCodec() {
        return JsonCodec.builder().build();
    }
}
