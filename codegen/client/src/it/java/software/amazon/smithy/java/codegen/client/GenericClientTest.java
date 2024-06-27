/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;


import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.http.HttpContext;
import software.amazon.smithy.java.runtime.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;


public class GenericClientTest {
    @Test
    public void echoTest() {
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("https://httpbin.org")
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
            public <I extends SerializableStruct, RequestT> void readBeforeTransmit(
                Context context,
                I input,
                Context.Value<RequestT> request
            ) {
                System.out.println("Sending request: " + request);
            }

            @Override
            public <I extends SerializableStruct, RequestT, ResponseT> void readBeforeDeserialization(
                Context context,
                I input,
                Context.Value<RequestT> request,
                Context.Value<ResponseT> response
            ) {
                System.out.println("GOT: " + response.toString());
            }

            @Override
            public <I extends SerializableStruct, RequestT> Context.Value<RequestT> modifyBeforeTransmit(
                Context context,
                I input,
                Context.Value<RequestT> request
            ) {
                return request.mapIf(HttpContext.HTTP_REQUEST, r -> r.withAddedHeaders("X-Foo", "Bar"));
            }
        };

        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .transport(new JavaHttpClientTransport(HttpClient.newHttpClient()))
            .endpoint("https://httpbin.org")
            .addInterceptor(interceptor)
            .build();

        var input = EchoInput.builder().string("hello world").build();
        var output = client.echo(input);
    }
}
