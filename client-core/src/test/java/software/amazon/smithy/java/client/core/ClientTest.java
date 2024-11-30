/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.plugins.ApplyModelRetryInfoPlugin;
import software.amazon.smithy.java.client.core.plugins.DefaultPlugin;
import software.amazon.smithy.java.client.core.plugins.InjectIdempotencyTokenPlugin;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.plugins.ApplyHttpRetryInfoPlugin;
import software.amazon.smithy.java.client.http.plugins.UserAgentPlugin;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class ClientTest {

    private static final Model MODEL = Model.assembler()
        .addUnparsedModel("test.smithy", """
            $version: "2"
            namespace smithy.example

            service Sprockets {}
            """)
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
                // User plugins are applied first.
                FooPlugin.class,
                // Default plugin always applied.
                DefaultPlugin.class,
                // DefaultPlugin applies these two:
                ApplyModelRetryInfoPlugin.class,
                InjectIdempotencyTokenPlugin.class,
                // The transport is applied as a plugin.
                JavaHttpClientTransport.class,
                // The transport automatically forwards plugin application to the HttpMessageExchange.
                HttpMessageExchange.class,
                // And HttpMessageExchange applies the UserAgentPlugin and ApplyHttpRetryInfoPlugin.
                UserAgentPlugin.class,
                ApplyHttpRetryInfoPlugin.class
            )
        );
    }

    @Test
    public void filtersPlugins() throws URISyntaxException {
        DynamicClient c = DynamicClient.builder()
            .model(MODEL)
            .service(SERVICE)
            .protocol(new RestJsonClientProtocol(SERVICE))
            .addPluginPredicate(clazz -> !clazz.equals(DefaultPlugin.class))
            .addPluginPredicate(clazz -> !clazz.equals(HttpMessageExchange.class))
            .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
            .build();

        assertThat(
            c.config().appliedPlugins(),
            contains(JavaHttpClientTransport.class)
        );
    }

    private static final class FooPlugin implements ClientPlugin {
        @Override
        public void configureClient(ClientConfig.Builder config) {}
    }
}
