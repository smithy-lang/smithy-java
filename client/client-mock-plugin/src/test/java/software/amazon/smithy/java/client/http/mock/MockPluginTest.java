/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ErrorFault;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.framework.model.InternalFailureException;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class MockPluginTest {

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    service Sprockets {
                        operations: [GetSprocket]
                        errors: [ServiceFooError]
                    }

                    @http(method: "POST", uri: "/s")
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                        errors: [InvalidSprocketId]
                    }

                    @error("client")
                    @httpError(429)
                    @retryable
                    structure InvalidSprocketId {}

                    @error("server")
                    @httpError(500)
                    structure ServiceFooError {}
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    @Test
    public void returnsMockedOutput() throws URISyntaxException {
        var mockQueue = new MockQueue();
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        mockQueue.enqueue(
                client.createStruct(
                        ShapeId.from("smithy.example#GetSprocketOutput"),
                        Document.of(Map.of("id", Document.of("a")))));
        mockQueue.enqueue(
                client.createStruct(
                        ShapeId.from("smithy.example#GetSprocketOutput"),
                        Document.of(Map.of("id", Document.of("b")))));

        var result1 = client.call("GetSprocket");
        assertThat(result1.getMember("id").asString(), equalTo("a"));

        var result2 = client.call("GetSprocket");
        assertThat(result2.getMember("id").asString(), equalTo("b"));

        assertThat(mock.getRequests(), hasSize(2));
    }

    @Test
    public void returnsMockedInternalError() throws URISyntaxException {
        var mockQueue = new MockQueue().enqueue(InternalFailureException.builder().message("Oh no").build());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        var e = Assertions.assertThrows(CallException.class, () -> client.call("GetSprocket"));
        assertThat(e.getFault(), equalTo(ErrorFault.SERVER));
        assertThat(e.isRetrySafe(), equalTo(RetrySafety.MAYBE));

        assertThat(mock.getRequests(), hasSize(1));
    }

    @Test
    public void returnsMockedExceptionsDirectly() throws URISyntaxException {
        var mockQueue = new MockQueue().enqueueError(InternalFailureException.builder().message("Oh no").build());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        Assertions.assertThrows(InternalFailureException.class, () -> client.call("GetSprocket"));
        assertThat(mock.getRequests(), hasSize(1));
    }

    @Test
    public void returnsMockedHttpResponses() throws URISyntaxException {
        AtomicReference<Boolean> ref = new AtomicReference<>();

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create().setStatusCode(404).addHeader("a", "b").toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterExecution(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
                        assertThat(error, not(nullValue()));
                        assertThat(hook.response(), not(nullValue()));
                        assertThat(hook.response(), instanceOf(HttpResponse.class));
                        var res = (HttpResponse) hook.response();
                        assertThat(res.headers().firstValue("a"), equalTo("b"));
                        ref.set(true);
                    }
                })
                .build();

        var e = Assertions.assertThrows(CallException.class, () -> client.call("GetSprocket"));
        assertThat(e.getFault(), is(ErrorFault.CLIENT));

        assertThat(mock.getRequests(), hasSize(1));

        // Make sure the interceptor was called.
        assertThat(ref.get(), is(true));
    }

    @Test
    public void returnsResultsBasedOnPredicates() throws URISyntaxException {
        var aQueue = new MockQueue();
        var bQueue = new MockQueue();

        var mock = MockPlugin.builder()
                .addMatcher(request -> {
                    if (request.input() instanceof Document d) {
                        if (d.getMember("id").asString().equals("a")) {
                            return aQueue.poll();
                        }
                    }
                    return null;
                })
                .addMatcher(request -> {
                    if (request.input() instanceof Document d) {
                        if (d.getMember("id").asString().equals("b")) {
                            return bQueue.poll();
                        }
                    }
                    return null;
                })
                .build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        // Because we need to use the dynamic client itself to enqueue these responses, we have to defer queueing them
        // instead of returning them directly from the matcher.
        aQueue.enqueue(
                client.createStruct(
                        ShapeId.from("smithy.example#GetSprocketOutput"),
                        Document.of(Map.of("id", Document.of("a")))));

        bQueue.enqueue(
                client.createStruct(
                        ShapeId.from("smithy.example#GetSprocketOutput"),
                        Document.of(Map.of("id", Document.of("b")))));

        var aresult = client.call("GetSprocket", Document.of(Map.of("id", Document.of("a"))));
        assertThat(aresult.getMember("id").asString(), equalTo("a"));

        var bresult = client.call("GetSprocket", Document.of(Map.of("id", Document.of("b"))));
        assertThat(bresult.getMember("id").asString(), equalTo("b"));

        assertThat(mock.getRequests(), hasSize(2));
    }

    @Test
    public void releasesPooledRequestBodiesAfterTheCallButRecordsReadableCopies() throws URISyntaxException {
        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .addHeader("content-type", "application/json")
                        .setBody(DataStream.ofBytes("{\"id\":\"a\"}".getBytes(StandardCharsets.UTF_8)))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();
        var liveBody = new AtomicReference<DataStream>();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        liveBody.set(((HttpRequest) hook.request()).body());
                    }
                })
                .build();

        var result = client.call("GetSprocket", Document.of(Map.of("id", Document.of("abc"))));
        assertThat(result.getMember("id").asString(), equalTo("a"));

        assertThat(liveBody.get(), instanceOf(PooledDataStream.class));
        var pooled = (PooledDataStream) liveBody.get();
        assertThat(pooled.escaped(), is(false));
        Assertions.assertThrows(IllegalStateException.class, pooled::asByteBuffer);

        assertThat(mock.getRequests(), hasSize(1));
        var recorded = mock.getRequests().get(0).request().body();
        assertThat(recorded, not(instanceOf(PooledDataStream.class)));
        assertThat(StandardCharsets.UTF_8.decode(recorded.asByteBuffer()).toString(), equalTo("{\"id\":\"abc\"}"));
    }

    @Test
    public void recordsReadableCopiesOfMetadataWrappedPooledBodies() throws URISyntaxException {
        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .addHeader("content-type", "application/json")
                        .setBody(DataStream.ofBytes("{\"id\":\"a\"}".getBytes(StandardCharsets.UTF_8)))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();
        var liveBody = new AtomicReference<DataStream>();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
                        var req = (HttpRequest) hook.request();
                        var wrapped = DataStream.withMetadata(req.body(), "application/x-custom", -1);
                        liveBody.set(wrapped);
                        return hook.asRequestType(req.toModifiable().setBody(wrapped));
                    }
                })
                .build();

        var result = client.call("GetSprocket", Document.of(Map.of("id", Document.of("abc"))));
        assertThat(result.getMember("id").asString(), equalTo("a"));

        assertThat(liveBody.get(), instanceOf(PooledDataStream.class));
        Assertions.assertThrows(IllegalStateException.class, () -> liveBody.get().asByteBuffer());

        assertThat(mock.getRequests(), hasSize(1));
        var recorded = mock.getRequests().get(0).request().body();
        assertThat(recorded, not(instanceOf(PooledDataStream.class)));
        assertThat(recorded.contentType(), equalTo("application/x-custom"));
        assertThat(StandardCharsets.UTF_8.decode(recorded.asByteBuffer()).toString(), equalTo("{\"id\":\"abc\"}"));
    }
}
