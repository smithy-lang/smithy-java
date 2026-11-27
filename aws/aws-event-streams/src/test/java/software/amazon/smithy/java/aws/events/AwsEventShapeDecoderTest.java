/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.events.model.BlobEvent;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.MyError;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestEventStream;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.aws.events.model.TestOperationOutput;
import software.amazon.smithy.java.aws.events.model.TestOperationWithException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.json.JsonCodec;

class AwsEventShapeDecoderTest {

    @Test
    public void testDecodeInitialResponse() {
        // Arrange
        var headers = HeadersBuilder.forEvent()
                .eventType("initial-response")
                .contentType("text/json")
                .put("intMemberHeader", 123)
                .build();
        var message = new Message(headers, "{\"stringMember\":\"Hello World!\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decodeInitialEvent(frame, null);

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
        var headers = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("headersOnlyMember")
                .put("sequenceNum", 123)
                .build();
        var message = new Message(headers, "{}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertInstanceOf(TestEventStream.HeadersOnlyMemberMember.class, actual);
        var expected = TestEventStream.builder()
                .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeStructureMember() {
        // Arrange
        var headers = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("structureMember")
                .build();
        var message = new Message(headers, "{\"foo\":\"memberFooValue\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertInstanceOf(TestEventStream.StructureMemberMember.class, actual);
        var expected = TestEventStream.builder()
                .structureMember(StructureEvent.builder().foo("memberFooValue").build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeBodyAndHeaderMember() {
        // Arrange
        var headers = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("bodyAndHeaderMember")
                .put("intMember", 123)
                .build();
        var message = new Message(headers, "{\"stringMember\":\"Hello world!\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertInstanceOf(TestEventStream.BodyAndHeaderMemberMember.class, actual);
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
        var headers = HeadersBuilder.forEvent()
                .contentType("text/plain")
                .eventType("stringMember")
                .build();
        var message = new Message(headers, "hello world!".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertInstanceOf(TestEventStream.StringMemberMember.class, actual);
        var expected = TestEventStream.builder()
                .stringMember(StringEvent.builder().payload("hello world!").build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeBlobMember() {
        // Arrange
        var headers = HeadersBuilder.forEvent()
                .contentType("application/octet-stream")
                .eventType("blobMember")
                .build();
        var bytes = new byte[] {0, 1, 2, (byte) 0xff};
        var message = new Message(headers, bytes);
        var frame = new AwsEventFrame(message);

        // Act
        var struct = createDecoder(TestOperation.instance()).decode(frame);

        // Assert
        assertInstanceOf(TestEventStream.class, struct);
        var actual = (TestEventStream) struct;
        assertInstanceOf(TestEventStream.BlobMemberMember.class, actual);
        var expected = TestEventStream.builder()
                .blobMember(BlobEvent.builder().payload(ByteBuffer.wrap(bytes)).build())
                .build();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeExceptionMember() {
        // Arrange
        var headers = HeadersBuilder.forException()
                .contentType("text/json")
                .exceptionType("modeledErrorMember")
                .build();
        var message = new Message(headers, "{\"message\":\"Client exception\"}".getBytes(StandardCharsets.UTF_8));
        var frame = new AwsEventFrame(message);

        // Act
        var decoder = createDecoder(TestOperationWithException.instance());
        var except = assertThrows(MyError.class, () -> decoder.decode(frame));

        // Assert
        assertEquals("Client exception", except.getMessage());
    }

    @Test
    public void testDecodeError() {
        // Arrange
        var headers = HeadersBuilder.forError()
                .put(":error-code", "InternalFailure")
                .put(":error-message", "An internal server error occurred")
                .build();
        var message = new Message(headers, new byte[0]);
        var frame = new AwsEventFrame(message);

        // Act
        Exception except = null;
        try {
            createDecoder(TestOperationWithException.instance()).decode(frame);
        } catch (Exception e) {
            except = e;
        }

        // Assert
        assertNotNull(except);
        assertInstanceOf(EventStreamingException.class, except);
        var eventStreamingException = (EventStreamingException) except;
        assertEquals(eventStreamingException.getErrorCode(), "InternalFailure");
        assertEquals(eventStreamingException.getMessage(), "An internal server error occurred");
    }

    @SuppressWarnings("unchecked")
    static <I extends SerializableStruct,
            O extends SerializableStruct> AwsEventShapeDecoder<?, ?> createDecoder(ApiOperation<I, O> operation) {
        return new AwsEventShapeDecoder<>(InitialEventType.INITIAL_RESPONSE,
                () -> operation.outputBuilder(), // output builder
                (Supplier) operation.outputEventBuilderSupplier(),
                operation.outputStreamMember(),
                createJsonCodec());
    }

    static Codec createJsonCodec() {
        return JsonCodec.builder().build();
    }
}
