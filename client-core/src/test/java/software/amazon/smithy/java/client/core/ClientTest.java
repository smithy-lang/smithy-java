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
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.useragent.UserAgentPlugin;
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

        assertThat(
            c.config().appliedPlugins(),
            contains(
                // User plugins are applied first.
                FooPlugin.class,
                // The transport is applied as a plugin.
                JavaHttpClientTransport.class,
                // The transport automatically forwards plugin application to the HttpMessageExchange.
                HttpMessageExchange.class,
                // And HttpMessageExchange applies the UserAgent plugin by default.
                UserAgentPlugin.class
            )
        );

        // Make sure it round trips.
        assertThat(c.config().appliedPlugins(), equalTo(c.config().toBuilder().build().appliedPlugins()));
    }

    private static final class FooPlugin implements ClientPlugin {
        @Override
        public void configureClient(ClientConfig.Builder config) {}
    }
}
