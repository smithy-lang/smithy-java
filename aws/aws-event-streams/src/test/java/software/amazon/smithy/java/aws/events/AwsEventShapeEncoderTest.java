/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.MyError;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestEventStream;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.aws.events.model.TestOperationInput;
import software.amazon.smithy.java.aws.events.model.TestOperationWithException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.json.JsonCodec;

class AwsEventShapeEncoderTest {

    @Test
    public void testEncodeInitialRequest() {
        // Arrange
        var encoder = createEncoder(TestOperation.instance());
        var event = TestOperationInput.builder()
                .headerString("headerValue")
                .inputStringMember("inputStringValue")
                .build();
        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("initial-request")
                .put("headerString", "headerValue")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{\"inputStringMember\":\"inputStringValue\"}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeHeadersOnlyMember() {
        // Arrange
        var encoder = createEncoder(TestOperation.instance());
        var event = TestEventStream.builder()
                .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = HeadersBuilder.forEvent()
                .eventType("headersOnlyMember")
                .put("sequenceNum", 123)
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeHeadersOnlyMemberWithEmitEmptyPayload() {
        // With emitEmptyPayload=true, a header-only event still emits an empty codec body and the
        // content-type header, instead of omitting both as the default (false) does.
        var encoder = createEncoder(TestOperation.instance(), true);
        var event = TestEventStream.builder()
                .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                .build();

        var result = encoder.encode(event);

        var expectedHeaders = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("headersOnlyMember")
                .put("sequenceNum", 123)
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeStructureMember() {
        // Arrange
        var encoder = createEncoder(TestOperation.instance());
        var event = TestEventStream.builder()
                .structureMember(StructureEvent.builder().foo("memberFooValue").build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("structureMember")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{\"foo\":\"memberFooValue\"}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeBodyAndHeaderMember() {
        // Arrange
        var encoder = createEncoder(TestOperation.instance());
        var event = TestEventStream.builder()
                .bodyAndHeaderMember(BodyAndHeaderEvent.builder()
                        .intMember(123)
                        .stringMember("Hello world!")
                        .build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = HeadersBuilder.forEvent()
                .contentType("text/json")
                .eventType("bodyAndHeaderMember")
                .put("intMember", 123)
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{\"stringMember\":\"Hello world!\"}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeStringMember() {
        // Arrange
        var encoder = createEncoder(TestOperation.instance());
        var event = TestEventStream.builder()
                .stringMember(StringEvent.builder().payload("hello world!").build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = HeadersBuilder.forEvent()
                .contentType("text/plain")
                .eventType("stringMember")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("hello world!", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeException() {
        // Arrange
        var encoder = createEncoder(TestOperationWithException.instance());
        var exception = MyError.builder().message("Event stream exception").build();

        // Act
        var result = encoder.encodeFailure(exception);

        // Assert
        var expectedHeaders = HeadersBuilder.forException()
                .contentType("text/json")
                .exceptionType("modeledErrorMember")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{\"message\":\"Event stream exception\"}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeError() {
        // Arrange
        var encoder = createEncoder(TestOperationWithException.instance());
        var exception = new NullPointerException("something caused a null pointer exception");

        // Act
        var result = encoder.encodeFailure(exception);

        // Assert
        var expectedHeaders = HeadersBuilder.forError()
                .put(":error-message", "Internal Server Error")
                .put(":error-code", "InternalServerException")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("", new String(result.unwrap().getPayload()));
    }

    static <I extends SerializableStruct,
            O extends SerializableStruct> AwsEventShapeEncoder createEncoder(ApiOperation<I, O> operation) {
        return createEncoder(operation, false);
    }

    static <I extends SerializableStruct, O extends SerializableStruct> AwsEventShapeEncoder createEncoder(
            ApiOperation<I, O> operation,
            boolean emitEmptyPayload
    ) {
        return new AwsEventShapeEncoder(InitialEventType.INITIAL_REQUEST,
                operation.outputStreamMember(), // event schema
                createJsonCodec(), // codec
                "text/json",
                emitEmptyPayload,
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    static Codec createJsonCodec() {
        return JsonCodec.builder().build();
    }

}
