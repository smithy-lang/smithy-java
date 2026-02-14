/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.eventstream.HeaderValue;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestEventStream;
import software.amazon.smithy.java.aws.events.model.TestOperation;
import software.amazon.smithy.java.aws.events.model.TestOperationInput;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.core.serde.event.FrameTransformer;
import software.amazon.smithy.java.json.JsonCodec;

class AwsEventShapeEncoderTest {

    @Test
    public void testEncodeInitialRequest() {
        // Arrange
        var encoder = createEncoder();
        var event = TestOperationInput.builder()
                .headerString("headerValue")
                .inputStringMember("inputStringValue")
                .build();
        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = new HeadersBuilder()
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
        var encoder = createEncoder();
        var event = TestEventStream.builder()
                .headersOnlyMember(HeadersOnlyEvent.builder().sequenceNum(123).build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = new HeadersBuilder()
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
        var encoder = createEncoder();
        var event = TestEventStream.builder()
                .structureMember(StructureEvent.builder().foo("memberFooValue").build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = new HeadersBuilder()
                .contentType("text/json")
                .eventType("structureMember")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("{\"foo\":\"memberFooValue\"}", new String(result.unwrap().getPayload()));
    }

    @Test
    public void testEncodeBodyAndHeaderMember() {
        // Arrange
        var encoder = createEncoder();
        var event = TestEventStream.builder()
                .bodyAndHeaderMember(BodyAndHeaderEvent.builder()
                        .intMember(123)
                        .stringMember("Hello world!")
                        .build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = new HeadersBuilder()
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
        var encoder = createEncoder();
        var event = TestEventStream.builder()
                .stringMember(StringEvent.builder().payload("hello world!").build())
                .build();

        // Act
        var result = encoder.encode(event);

        // Assert
        var expectedHeaders = new HeadersBuilder()
                .contentType("text/json")
                .eventType("stringMember")
                .build();
        assertEquals(expectedHeaders, result.unwrap().getHeaders());
        assertEquals("\"hello world!\"", new String(result.unwrap().getPayload()));
    }

    static AwsEventShapeEncoder createEncoder() {
        return new AwsEventShapeEncoder(InitialEventType.INITIAL_REQUEST,
                TestOperation.instance().inputStreamMember(), // event schema
                createJsonCodec(), // codec
                "text/json",
                FrameTransformer.identity(),
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    static Codec createJsonCodec() {
        return JsonCodec.builder().build();
    }

    static class HeadersBuilder {
        private final Map<String, HeaderValue> headers = new HashMap<>();

        HeadersBuilder() {
            headers.put(":message-type", HeaderValue.fromString("event"));
        }

        public HeadersBuilder messageType(String messageType) {
            headers.put(":message-type", HeaderValue.fromString(messageType));
            return this;
        }

        public HeadersBuilder contentType(String contentType) {
            headers.put(":content-type", HeaderValue.fromString(contentType));
            return this;
        }

        public HeadersBuilder eventType(String eventType) {
            headers.put(":event-type", HeaderValue.fromString(eventType));
            return this;
        }

        public HeadersBuilder put(String name, String value) {
            headers.put(name, HeaderValue.fromString(value));
            return this;
        }

        public HeadersBuilder put(String name, int value) {
            headers.put(name, HeaderValue.fromInteger(value));
            return this;
        }

        public Map<String, HeaderValue> build() {
            return headers;
        }
    }
}
