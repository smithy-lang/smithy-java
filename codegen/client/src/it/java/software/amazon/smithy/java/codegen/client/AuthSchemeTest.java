/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.codegen.client.util.EchoServer;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.traits.HttpBasicAuthTrait;

public class AuthSchemeTest {
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
    void defaultAuthSchemesAdded() {
        var interceptor = new ClientInterceptor() {
            @Override
            public void readBeforeTransmit(RequestHook<?, ?> hook) {
                var request = (SmithyHttpRequest) hook.request();
                var signatureValue = request.headers().firstValue(TestAuthScheme.SIGNATURE_HEADER);
                assertTrue(signatureValue.isPresent());
                assertEquals("smithy-test-signature", signatureValue.get());
            }
        };
        var client = TestServiceClient.builder()
            .protocol(new RestJsonClientProtocol())
            .endpoint(ENDPOINT)
            .authSchemeResolver(params -> List.of(new AuthSchemeOption(HttpBasicAuthTrait.ID)))
            .addInterceptor(interceptor)
            .value(2L)
            .build();

        var value = "hello world";
        var input = EchoInput.builder().string(value).build();
        var output = client.echo(input);
        assertEquals(value, output.string());
    }
}
