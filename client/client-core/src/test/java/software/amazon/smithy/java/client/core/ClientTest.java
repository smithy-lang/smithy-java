/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.core.interceptors.CallHook;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.client.core.plugins.ApplyModelRetryInfoPlugin;
import software.amazon.smithy.java.client.core.plugins.DefaultPlugin;
import software.amazon.smithy.java.client.core.plugins.InjectIdempotencyTokenPlugin;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.client.http.plugins.ApplyHttpRetryInfoPlugin;
import software.amazon.smithy.java.client.http.plugins.UserAgentPlugin;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class ClientTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    service Sprockets {
                        operations: [GetSprocket]
                    }

                    @http(method: "POST", uri: "/s")
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                    }
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    @Test
    public void tracksPlugins() throws URISyntaxException {
        DynamicClient c = DynamicClient.builder()
                .model(MODEL)
                .service(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .addPlugin(new FooPlugin())
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .build();

        // Make sure that applied plugins round-trip.
        assertThat(c.config().appliedPlugins(), equalTo(c.config().toBuilder().build().appliedPlugins()));

        assertThat(
                c.config().appliedPlugins(),
                contains(
                        // Default plugin always applied.
                        DefaultPlugin.class,
                        // DefaultPlugin applies these two:
                        ApplyModelRetryInfoPlugin.class,
                        InjectIdempotencyTokenPlugin.class,
                        // The transport is applied as a plugin, before user plugins.
                        JavaHttpClientTransport.class,
                        // The transport automatically forwards plugin application to the HttpMessageExchange.
                        HttpMessageExchange.class,
                        // And HttpMessageExchange applies the UserAgentPlugin and ApplyHttpRetryInfoPlugin.
                        UserAgentPlugin.class,
                        ApplyHttpRetryInfoPlugin.class,
                        // User plugins are applied last.
                        FooPlugin.class));
    }

    private static final class FooPlugin implements ClientPlugin {
        @Override
        public void configureClient(ClientConfig.Builder config) {}
    }

    @Test
    public void correctlyWrapsTransportExceptions() throws URISyntaxException {
        var expectedException = new IOException("A");
        var queue = new MockQueue();
        queue.enqueueError(expectedException);

        DynamicClient c = DynamicClient.builder()
                .model(MODEL)
                .service(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        var exception = Assertions.assertThrows(TransportException.class, () -> c.call("GetSprocket"));
        assertSame(exception.getCause(), expectedException);
    }

    @Test
    public void allowsInterceptorRequestOverrides() throws URISyntaxException {
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.builder().statusCode(200).build());
        var id = "abc";

        DynamicClient c = DynamicClient.builder()
                .model(MODEL)
                .service(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin(config -> config.addInterceptor(new ClientInterceptor() {
                    @Override
                    public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
                        var override = RequestOverrideConfig.builder()
                                .putConfig(ClientContext.APPLICATION_ID, id)
                                .build();
                        return hook.config().withRequestOverride(override);
                    }

                    @Override
                    public void readBeforeExecution(InputHook<?, ?> hook) {
                        assertThat(hook.context().get(ClientContext.APPLICATION_ID), equalTo(id));
                    }
                }))
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        c.call("GetSprocket");
    }

    @Test
    public void requestOverridesPerCallTakePrecedence() throws URISyntaxException {
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.builder().statusCode(200).build());
        var id = "abc";

        DynamicClient c = DynamicClient.builder()
                .model(MODEL)
                .service(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin(config -> config.addInterceptor(new ClientInterceptor() {
                    @Override
                    public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
                        //RequestOverrides config should be visible here.
                        assertThat(hook.config().context().get(CallContext.APPLICATION_ID), equalTo(id));
                        // Note that the overrides given to the call itself will override interceptors.
                        var override = RequestOverrideConfig.builder()
                                .putConfig(ClientContext.APPLICATION_ID, "foo")
                                .build();
                        return hook.config().withRequestOverride(override);
                    }

                    @Override
                    public void readBeforeExecution(InputHook<?, ?> hook) {
                        assertThat(hook.context().get(ClientContext.APPLICATION_ID), equalTo(id));
                        assertThat(hook.context().get(ClientContext.API_CALL_TIMEOUT), equalTo(Duration.ofMinutes(2)));
                    }
                }))
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        // Provide request-level overrides here.
        c.call("GetSprocket",
                Document.ofObject(new HashMap<>()),
                RequestOverrideConfig.builder()
                        .putConfig(ClientContext.API_CALL_TIMEOUT, Duration.ofMinutes(2))
                        .putConfig(ClientContext.APPLICATION_ID, id) // this will be take precedence
                        .build());
    }

    @Test
    public void setsCustomEndpoint() {
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.builder().statusCode(200).build());

        DynamicClient c = DynamicClient.builder()
                .model(MODEL)
                .service(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin(config -> config.addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readBeforeExecution(InputHook<?, ?> hook) {
                        assertThat(hook.context().get(ClientContext.CUSTOM_ENDPOINT).uri().toString(),
                                equalTo("https://example.com"));
                    }
                }))
                .endpoint("https://example.com")
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();

        c.call("GetSprocket");
    }
}
