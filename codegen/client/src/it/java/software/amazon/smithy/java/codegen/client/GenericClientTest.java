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
import software.amazon.smithy.java.codegen.client.settings.AbSetting;
import software.amazon.smithy.java.codegen.client.util.EchoServer;
import software.amazon.smithy.java.runtime.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.InputHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;

public class GenericClientTest {
    private static final EchoServer server = new EchoServer();
    private static final int PORT = 8888;
    private static final String ENDPOINT = "http://127.0.0.1:" + PORT;

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
        System.out.println("DOING THINGS");
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
            .endpoint(ENDPOINT)
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
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, RequestT> hook) {
                return hook.mapRequest(SmithyHttpRequest.class, request -> request.withAddedHeaders("X-Foo", "Bar"));
            }

            @Override
            public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
                if (hook.response() instanceof SmithyHttpResponse response) {
                    var value = response.headers().map().get(header);
                    assertNotNull(value);
                    assertEquals(value.get(0), "[Bar]");
                }
            }
        };

        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
            .endpoint(ENDPOINT)
            .addInterceptor(interceptor)
            .value(2.2)
            .build();

        var echoedString = "hello world";
        var input = EchoInput.builder().string(echoedString).build();
        var output = client.echo(input);
        assertEquals(echoedString, output.string());
    }

    // TODO: Update to use context directly once we have a method that returns that
    @Test
    public void correctlyAppliesDefaultPlugins() {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeExecution(InputHook<?> hook) {
                var constant = hook.context().get(TestClientPlugin.CONSTANT_KEY);
                assertEquals(constant, "CONSTANT");
                var value = hook.context().get(TestClientPlugin.VALUE_KEY);
                assertEquals(value, BigDecimal.valueOf(2L));
                var ab = hook.context().get(AbSetting.AB_KEY);
                assertEquals(ab, "ab");
                var singleVarargs = hook.context().get(TestClientPlugin.STRING_LIST_KEY);
                assertEquals(List.of("a", "b", "c", "d"), singleVarargs);
                var foo = hook.context().get(TestClientPlugin.FOO_KEY);
                assertEquals(foo, "string");
                var multiVarargs = hook.context().get(TestClientPlugin.BAZ_KEY);
                assertEquals(List.of("a", "b", "c"), multiVarargs);
                var overridden = hook.context().get(TestClientPlugin.OVERRIDE_KEY);
                assertEquals(overridden, "override works");
                var nested = hook.context().get(TestClientPlugin.NESTED_KEY);
                assertEquals(nested, 1);
            }
        };
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
            .endpoint(ENDPOINT)
            .addInterceptor(interceptor)
            .value(2L)
            .multiValue("a", "b")
            .multiVarargs("string", "a", "b", "c")
            .singleVarargs("a", "b", "c", "d")
            .overridden("override works")
            .nested(1)
            .build();

        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        assertEquals(value, output.string());
    }
}
