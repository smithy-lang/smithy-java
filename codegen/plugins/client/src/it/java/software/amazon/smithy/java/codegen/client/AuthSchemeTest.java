/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.codegen.client.util.EchoServer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.http.api.HttpRequest;

public class AuthSchemeTest {
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
    void defaultAuthSchemesAdded() {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                var request = (HttpRequest) hook.request();
                var signatureValue = request.headers().firstValue(TestAuthScheme.SIGNATURE_HEADER);
                assertEquals("smithy-test-signature", signatureValue);
            }
        };
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol(PreludeSchemas.DOCUMENT.id()))
            .endpointResolver(ENDPOINT_RESOLVER)
            .authSchemeResolver(params -> List.of(new AuthSchemeOption(TestAuthSchemeTrait.ID)))
            .addInterceptor(interceptor)
            .value(2L)
            .build();

        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        assertEquals(value, output.string());
    }
}
