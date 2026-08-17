/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.core.serde.event.EventStreamReader;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.protocoltests.traits.eventstream.Event;
import software.amazon.smithy.protocoltests.traits.eventstream.EventHeaderValue;
import software.amazon.smithy.protocoltests.traits.eventstream.EventStreamTestCase;
import software.amazon.smithy.protocoltests.traits.eventstream.EventType;

/**
 * Provides client test cases for {@link EventStreamTestCase}'s. See also the {@link EventStreamClientTests} annotation.
 */
final class EventStreamClientTestsProtocolTestProvider extends
        ProtocolTestProvider<EventStreamClientTests, ProtocolTestExtension.SharedClientTestData> {

    @Override
    protected Class<EventStreamClientTests> getAnnotationType() {
        return EventStreamClientTests.class;
    }

    @Override
    protected Class<ProtocolTestExtension.SharedClientTestData> getSharedTestDataType() {
        return ProtocolTestExtension.SharedClientTestData.class;
    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
            ProtocolTestExtension.SharedClientTestData store,
            EventStreamClientTests annotation,
            TestFilter filter
    ) {
        return store.operations()
                .stream()
                .flatMap(operation -> operation.eventStreamTestCases()
                        .stream()
                        .flatMap(testCase -> {
                            if (filter.skipOperation(operation.id()) || filter.skipTestCase(testCase)) {
                                return Stream.of(new IgnoredTestCase(testCase.getId()));
                            }
                            // Run each event-stream test through the codegen model and (when available) the
                            // document-backed dynamic model.
                            return TestModes.available(operation)
                                    .map(mode -> {
                                        var name = testCase.getId() + " [" + mode.label() + "]";
                                        if (filter.skipTestCase(testCase, mode)) {
                                            return new IgnoredTestCase(name);
                                        }
                                        try {
                                            return buildContext(store, operation, testCase, mode);
                                        } catch (RuntimeException e) {
                                            return new FailedGenerationTestCase(name, e);
                                        }
                                    });
                        }));
    }

    private TestTemplateInvocationContext buildContext(
            ProtocolTestExtension.SharedClientTestData store,
            HttpTestOperation operation,
            EventStreamTestCase testCase,
            TestMode mode
    ) {
        var apiOperation = operation.operationModel(mode);
        var testProtocol = store.getProtocol(testCase.getProtocol());
        var overrideConfig = RequestOverrideConfig.builder()
                .protocol(testProtocol)
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();
        var writer = apiOperation.inputStreamMember() != null ? EventStream.newWriter() : null;
        var input = buildInput(writer, apiOperation, testCase.getInitialRequestParams());

        if (testCase.getInitialRequest().isPresent()) {
            return new RequestTestInvocationContext(
                    testCase,
                    mode,
                    null,
                    store.mockClient(),
                    apiOperation,
                    input,
                    null,
                    writer,
                    overrideConfig,
                    new RequestTestTransport());
        }

        if (testCase.getInitialResponse().isPresent()) {
            var outputBuilder = apiOperation.outputBuilder();
            testCase.getInitialResponseParams()
                    .ifPresent(params -> new ProtocolTestDocument(params, null).deserializeInto(outputBuilder));
            return new ResponseTestInvocationContext(
                    testCase,
                    mode,
                    null,
                    store.mockClient(),
                    apiOperation,
                    input,
                    outputBuilder.errorCorrection().build(),
                    writer,
                    overrideConfig,
                    new InitialResponseTestTransport(testCase.getInitialResponse().get()));
        }

        var event = testCase.getEvents().getFirst(); // Currently each test case only has one event.
        if (event.getType().equals(EventType.REQUEST)) {
            var eventBuilder = apiOperation.inputEventBuilderSupplier().get();
            event.getParams()
                    .ifPresent(params -> new ProtocolTestDocument(params, null).deserializeInto(eventBuilder));
            return new RequestTestInvocationContext(
                    testCase,
                    mode,
                    event,
                    store.mockClient(),
                    apiOperation,
                    input,
                    eventBuilder.build(),
                    writer,
                    overrideConfig,
                    new RequestTestTransport());
        } else {
            SerializableStruct expectedEvent = null;
            if (event.getParams().isPresent()) {
                var eventBuilder = apiOperation.outputEventBuilderSupplier().get();
                new ProtocolTestDocument(event.getParams().get(), null).deserializeInto(eventBuilder);
                expectedEvent = eventBuilder.build();
            }
            return new ResponseTestInvocationContext(
                    testCase,
                    mode,
                    event,
                    store.mockClient(),
                    apiOperation,
                    input,
                    expectedEvent,
                    writer,
                    overrideConfig,
                    new ResponseTestTransport(event));
        }
    }

    private record RequestTestInvocationContext(
            EventStreamTestCase testCase,
            TestMode mode,
            Event event,
            MockClient mockClient,
            ApiOperation apiOperation,
            SerializableStruct input,
            SerializableStruct expected,
            EventStream<SerializableStruct> writer,
            RequestOverrideConfig overrideConfig,
            RequestTestTransport testTransport) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId() + " [" + mode.label() + "]";
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                // Bind this test's transport just before sending, not during generation: contexts for every
                // test/mode are built up front, so binding at generation would let the last one win.
                var placeholderTransport =
                        (MockClient.PlaceHolderTransport<HttpRequest, HttpResponse>) mockClient.config().transport();
                placeholderTransport.setTransport(testTransport);
                if (event != null) { // normal request event.
                    Thread.ofVirtual().start(() -> {
                        try (var w = writer.asWriter()) {
                            w.write(expected);
                        }
                    });
                }
                try {
                    mockClient.clientRequest(input, apiOperation, overrideConfig);
                    var request = testTransport.getCapturedRequest();
                    if (event != null) {
                        Assertions.assertEventStreamRequestEquals(request, event);
                    } else {
                        Assertions.assertInitialRequestEquals(testCase, request);
                    }
                } finally {
                    writer.close();
                }
            });
        }
    }

    private record ResponseTestInvocationContext(
            EventStreamTestCase testCase,
            TestMode mode,
            Event event,
            MockClient mockClient,
            ApiOperation apiOperation,
            SerializableStruct input,
            SerializableStruct expected,
            EventStream<?> writer,
            RequestOverrideConfig overrideConfig,
            ClientTransport<HttpRequest, HttpResponse> testTransport) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId() + " [" + mode.label() + "]";
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                // Bind this test's transport just before sending, not during generation: contexts for every
                // test/mode are built up front, so binding at generation would let the last one win.
                var placeholderTransport =
                        (MockClient.PlaceHolderTransport<HttpRequest, HttpResponse>) mockClient.config().transport();
                placeholderTransport.setTransport(testTransport);
                try {
                    var output = mockClient.clientRequest(input, apiOperation, overrideConfig);
                    var actual = output;
                    if (event != null) { // Normal response event
                        EventStreamReader<SerializableStruct> reader =
                                output.getMemberValue(apiOperation.outputStreamMember());
                        actual = reader.read();
                    }
                    if (testCase.getExpectation().isFailure()) {
                        fail("Expected failure but got: " + actual);
                    }
                    // Ignore stream field for initial response comparison.
                    assertThat(actual)
                            .usingRecursiveComparison(ComparisonUtils.getComparisonConfig())
                            .ignoringFields("stream")
                            .isEqualTo(expected);
                } catch (Exception e) {
                    if (testCase.getExpectation().isFailure()) {
                        Assertions.assertExpectationEquals(testCase, e);
                        return;
                    }
                    throw e;
                } finally {
                    if (writer != null) {
                        writer.close();
                    }
                }
            });
        }
    }

    private static final class RequestTestTransport implements ClientTransport<HttpRequest, HttpResponse> {
        private static final HttpResponse DUMMY_RESPONSE = HttpResponse.create()
                .setStatusCode(555)
                .toUnmodifiable();

        private HttpRequest capturedRequest;

        public HttpRequest getCapturedRequest() {
            return capturedRequest;
        }

        @Override
        public HttpResponse send(Context context, HttpRequest request) {
            this.capturedRequest = request;
            return DUMMY_RESPONSE;
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    private record ResponseTestTransport(Event event) implements ClientTransport<HttpRequest, HttpResponse> {

        private static byte[] buildFrameBytes(Event event) {
            var headers = new HashMap<String, HeaderValue>();
            for (var entry : event.getHeaders().entrySet()) {
                headers.put(entry.getKey(), toHeaderValue(entry.getValue()));
            }
            byte[] payload = event.getBody()
                    .map(b -> decodeBody(b, event.getBodyMediaType()))
                    .orElse(new byte[0]);
            var message = new Message(headers, payload);
            var buf = message.toByteBuffer();
            var bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        }

        private static HeaderValue toHeaderValue(EventHeaderValue<?> value) {
            return switch (value.getType()) {
                case BOOLEAN ->
                    HeaderValue.fromBoolean(value.asBoolean());
                case BYTE -> HeaderValue.fromByte(value.asByte());
                case SHORT -> HeaderValue.fromShort(value.asShort());
                case INTEGER ->
                    HeaderValue.fromInteger(value.asInteger());
                case LONG -> HeaderValue.fromLong(value.asLong());
                case BLOB -> HeaderValue.fromByteArray(value.asBlob());
                case STRING -> HeaderValue.fromString(value.asString());
                case TIMESTAMP ->
                    HeaderValue.fromTimestamp(value.asTimestamp());
            };
        }

        @Override
        public HttpResponse send(Context context, HttpRequest request) {
            byte[] frameBytes;
            if (event.getBytes().isPresent()) {
                frameBytes = event.getBytes().get();
            } else {
                frameBytes = buildFrameBytes(event);
            }

            return HttpResponse.create()
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setStatusCode(200)
                    .setHeaders(HttpHeaders.of(Map.of(
                            "content-type",
                            List.of("application/vnd.amazon.eventstream"))))
                    .setBody(DataStream.ofBytes(frameBytes, "application/vnd.amazon.eventstream"))
                    .toUnmodifiable();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    private record InitialResponseTestTransport(ObjectNode response)
            implements ClientTransport<HttpRequest, HttpResponse> {

        @Override
        public HttpResponse send(Context context, HttpRequest request) {
            var builder = HttpResponse.create()
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setStatusCode(response.expectNumberMember("code").getValue().intValue());

            response.getObjectMember("headers").ifPresent(headers -> {
                Map<String, List<String>> headerMap = new HashMap<>();
                for (var headerEntry : headers.getMembers().entrySet()) {
                    headerMap.put(headerEntry.getKey().getValue(),
                            List.of(headerEntry.getValue().expectStringNode().getValue()));
                }
                response.getStringMember("bodyMediaType")
                        .ifPresent(mediaType -> headerMap.put("content-type", List.of(mediaType.getValue())));
                builder.setHeaders(HttpHeaders.of(headerMap));
            });

            response.getStringMember("body").ifPresent(body -> {
                var mediaType = response.getStringMember("bodyMediaType")
                        .map(StringNode::getValue);
                builder.setBody(DataStream.ofBytes(
                        decodeBody(body.getValue(), mediaType),
                        mediaType.orElse(null)));
            });
            return builder.toUnmodifiable();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    private static byte[] decodeBody(String body, Optional<String> mediaType) {
        return mediaType
                .filter(ProtocolTestProvider::isBinaryMediaType)
                .map(type -> Base64.getDecoder().decode(body))
                .orElseGet(() -> body.getBytes(StandardCharsets.UTF_8));
    }

    private static SerializableStruct buildInput(
            EventStream<?> writer,
            ApiOperation<?, ?> operation,
            Optional<ObjectNode> params
    ) {
        var inputBuilder = operation.inputBuilder();
        if (writer != null) {
            inputBuilder.setMemberValue(operation.inputStreamMember(), writer);
        }
        params.ifPresent(p -> new ProtocolTestDocument(p, null).deserializeInto(inputBuilder));
        return inputBuilder.errorCorrection().build();
    }
}
