/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.codegen.client.settings.AbSetting;
import software.amazon.smithy.java.codegen.client.settings.TestSettings;
import software.amazon.smithy.java.codegen.client.util.EchoServer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.model.shapes.ShapeId;

public class GenericClientTest {
    private static final EchoServer server = new EchoServer();
    private static final int PORT = 8888;
    private static final EndpointResolver ENDPOINT_RESOLVER = EndpointResolver.staticHost("http://127.0.0.1:" + PORT);

    @BeforeEach
    public void setup() {
        server.start(PORT);
    }

    @AfterEach
    public void teardown() {
        server.stop();
    }

    @Test
    public void echoTest() {
        var client = TestServiceClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .endpointResolver(ENDPOINT_RESOLVER)
                .value(5L)
                .build();

        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        assertEquals(value, output.string());
    }

    @Test
    public void supportsInterceptors() {
        var header = "x-foo";
        var interceptor = new ClientInterceptor() {
            @Override
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
                return hook.mapRequest(
                        HttpRequest.class,
                        h -> h.request().toBuilder().withAddedHeader("X-Foo", "Bar").build());
            }

            @Override
            public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
                if (hook.response() instanceof HttpResponse response) {
                    var value = response.headers().map().get(header);
                    assertNotNull(value);
                    assertEquals(value.get(0), "[Bar]");
                }
            }
        };

        var client = TestServiceClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .endpointResolver(ENDPOINT_RESOLVER)
                .addInterceptor(interceptor)
                .value(2.2)
                .build();

        var echoedString = "hello world";
        var input = EchoInput.builder().string(echoedString).build();
        var output = client.echo(input);
        assertEquals(echoedString, output.string());
    }

    @Test
    public void customerOverrideWorksCorrectly() {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeExecution(InputHook<?, ?> hook) {
                var constant = hook.context().get(TestClientPlugin.CONSTANT_KEY);
                assertEquals(constant, "CONSTANT");
                var value = hook.context().get(TestSettings.VALUE_KEY);
                assertEquals(value, BigDecimal.valueOf(4L));
                var ab = hook.context().get(AbSetting.AB_KEY);
                assertEquals(ab, "override1override2");
                var singleVarargs = hook.context().get(TestSettings.STRING_LIST_KEY);
                assertEquals(List.of("e", "f", "g", "h"), singleVarargs);
                var foo = hook.context().get(TestSettings.FOO_KEY);
                assertEquals(foo, "stringOverride");
                var multiVarargs = hook.context().get(TestSettings.BAZ_KEY);
                assertEquals(List.of("aOverride", "bOverride"), multiVarargs);
                var nested = hook.context().get(TestSettings.NESTED_KEY);
                assertEquals(nested, 2);
            }
        };
        var client = TestServiceClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .endpointResolver(ENDPOINT_RESOLVER)
                .addInterceptor(interceptor)
                .value(2L)
                .multiValue("a", "b")
                .multiVarargs("string", "a", "b", "c")
                .singleVarargs("a", "b", "c", "d")
                .nested(1)
                .build();
        var override = TestServiceClient.requestOverrideBuilder()
                .value(4L)
                .multiValue("override1", "override2")
                .multiVarargs("stringOverride", "aOverride", "bOverride")
                .singleVarargs("e", "f", "g", "h")
                .nested(2)
                .build();
        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        client.echo(input, override);
    }

    @Test
    public void correctlyAppliesDefaultPlugins() {
        var client = TestServiceClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .endpointResolver(ENDPOINT_RESOLVER)
                .value(2L)
                .multiValue("a", "b")
                .multiVarargs("string", "a", "b", "c")
                .singleVarargs("a", "b", "c", "d")
                .nested(1)
                .build();
        var context = client.config().context();
        assertEquals("CONSTANT", context.expect(TestClientPlugin.CONSTANT_KEY));
        assertEquals(BigDecimal.valueOf(2L), context.expect(TestSettings.VALUE_KEY));
        assertEquals("ab", context.expect(AbSetting.AB_KEY));
        assertEquals(List.of("a", "b", "c", "d"), context.expect(TestSettings.STRING_LIST_KEY));
        assertEquals("string", context.expect(TestSettings.FOO_KEY));
        assertEquals(List.of("a", "b", "c"), context.expect(TestSettings.BAZ_KEY));
        assertEquals(1, context.expect(TestSettings.NESTED_KEY));
        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        assertEquals(value, output.string());
    }

    @Test
    public void generatedApiService() {
        var client = TestServiceClient.builder()
                .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
                .endpointResolver(ENDPOINT_RESOLVER)
                .build();

        assertEquals(client.config().service().schema().id(),
                ShapeId.from("smithy.java.codegen.server.test#TestService"));
    }
}
