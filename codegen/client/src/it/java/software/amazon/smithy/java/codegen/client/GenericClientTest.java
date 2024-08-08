/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.InputHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;


public class GenericClientTest {
    @Test
    public void echoTest() {
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .endpoint("https://httpbin.org")
            .value(5L)
            .build();

        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        System.out.println(output);
    }

    @Test
    public void supportsInterceptors() {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeTransmit(RequestHook<?, ?> hook) {
                System.out.println("Sending request: " + hook.request());
            }

            @Override
            public void readBeforeDeserialization(ResponseHook<?, ?, ?> hook) {
                System.out.println("GOT: " + hook.response().toString());
            }

            @Override
            public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, RequestT> hook) {
                return hook.mapRequest(SmithyHttpRequest.class, request -> {
                    return request.withAddedHeaders("X-Foo", "Bar");
                });
            }
        };

        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .endpoint("https://httpbin.org")
            .addInterceptor(interceptor)
            .value(2.2)
            .build();

        var input = EchoInput.builder().string("hello world").build();
        var output = client.echo(input);
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
                var ab = hook.context().get(TestClientPlugin.AB_KEY);
                assertEquals(ab, "ab");
                var singleVarargs = hook.context().get(TestClientPlugin.STRING_LIST_KEY);
                assertEquals(List.of("a", "b", "c", "d"), singleVarargs);
                var foo = hook.context().get(TestClientPlugin.FOO_KEY);
                assertEquals(foo, "string");
                var multiVarargs = hook.context().get(TestClientPlugin.BAZ_KEY);
                assertEquals(List.of("a", "b", "c"), multiVarargs);
            }
        };
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("https://httpbin.org")
            .addInterceptor(interceptor)
            .value(2L)
            .multiValue("a", "b")
            .multiVarargs("string", "a", "b", "c")
            .singleVarargs("a", "b", "c", "d")
            .build();

        var input = EchoInput.builder().string("hello world").build();
        var output = client.echo(input);
    }
}
