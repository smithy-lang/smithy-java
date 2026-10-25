/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.aws.client.core.AwsCredentialChainPlugin;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class SigV4AuthSchemeClientTest {

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");
    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    service Sprockets {
                        operations: [CreateSprocket]
                    }

                    operation CreateSprocket {
                        input := {}
                        output := {}
                    }
                    """)
            .assemble()
            .unwrap();

    @Test
    public void signsWithStaticCredentialsRegisteredViaIdentityResolverOf() {
        var mockQueue = new MockQueue();
        mockQueue.enqueue(HttpResponse.create()
                .setStatusCode(200)
                .setBody(DataStream.ofString("{}"))
                .toUnmodifiable());
        var authorization = new AtomicReference<String>();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .protocol(new AwsJson1Protocol(SERVICE))
                .endpointResolver(EndpointResolver.staticEndpoint("https://sprockets.us-east-1.amazonaws.com"))
                .putConfig(RegionSetting.REGION, "us-east-1")
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(SigV4Trait.ID)))
                .putSupportedAuthSchemes(new SigV4AuthScheme("sprockets"))
                // IdentityResolver.of reports the hidden record class, not AwsCredentialsIdentity.
                .addIdentityResolver(IdentityResolver.of(AwsCredentialsIdentity.create("AKID", "secret")))
                // Codegen adds this plugin to every SigV4 client; it must not replace the user's credentials.
                .addPlugin(new AwsCredentialChainPlugin())
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        authorization.set(((HttpRequest) hook.request()).headers().firstValue("authorization"));
                    }
                })
                .build();

        client.call("CreateSprocket");

        assertThat(authorization.get(), notNullValue());
        assertThat(authorization.get(), containsString("Credential=AKID/"));
    }
}
