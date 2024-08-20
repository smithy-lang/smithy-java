/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.client.TestServiceClient;
import smithy.java.codegen.server.test.model.EchoInput;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.aws.restjson1.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

public class AuthSchemeTest {
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
            .authSchemeResolver(params -> List.of(AuthSchemeOption.forId(TestAuthScheme.ID)))
            .endpoint("https://httpbin.org")
            .addInterceptor(interceptor)
            .value(2L)
            .build();

        var input = EchoInput.builder().string("hello world").build();
        var output = client.echo(input);
    }
}
