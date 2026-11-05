/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.auth.SigV4ATrait;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.aws.client.core.AwsCredentialChainPlugin;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class SigV4aAuthSchemeClientTest {

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
    public void factoryCreatesSchemeFromTrait() {
        var scheme = new SigV4aAuthScheme.Factory().createAuthScheme(SigV4ATrait.builder().name("widgets").build());

        assertThat(scheme.schemeId()).isEqualTo(ShapeId.from("aws.auth#sigv4a"));
        assertThat(new SigV4aAuthScheme.Factory().schemeId()).isEqualTo(SigV4ATrait.ID);
        assertThat(scheme.requestClass()).isEqualTo(HttpRequest.class);
        assertThat(scheme.identityClass()).isEqualTo(AwsCredentialsIdentity.class);
    }

    @Test
    public void signsWithConfiguredRegionSet() {
        var mockQueue = new MockQueue();
        mockQueue.enqueue(HttpResponse.create()
                .setStatusCode(200)
                .setBody(DataStream.ofString("{}"))
                .toUnmodifiable());
        var signed = new AtomicReference<HttpRequest>();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .protocol(new AwsJson1Protocol(SERVICE))
                .endpointResolver(EndpointResolver.staticEndpoint("https://sprockets.us-east-1.amazonaws.com"))
                .putConfig(RegionSetting.REGION, "us-east-1")
                .putConfig(SigV4aSettings.SIGNING_REGION_SET, List.of("us-east-1", "us-west-2"))
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(SigV4ATrait.ID)))
                .putSupportedAuthSchemes(new SigV4aAuthScheme("sprockets"))
                .addIdentityResolver(IdentityResolver.of(AwsCredentialsIdentity.create("AKID", "secret")))
                .addPlugin(new AwsCredentialChainPlugin())
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        signed.set((HttpRequest) hook.request());
                    }
                })
                .build();

        client.call("CreateSprocket");

        var headers = signed.get().headers();
        assertThat(headers.firstValue("x-amz-region-set")).isEqualTo("us-east-1,us-west-2");
        assertThat(headers.firstValue("authorization"))
                .startsWith("AWS4-ECDSA-P256-SHA256 Credential=AKID/")
                .contains("/sprockets/aws4_request, SignedHeaders=")
                .contains("x-amz-region-set");
    }

    @Test
    public void endpointAuthSchemeSwapsToSigV4aAndSuppliesRegionSet() {
        var mockQueue = new MockQueue();
        mockQueue.enqueue(HttpResponse.create()
                .setStatusCode(200)
                .setBody(DataStream.ofString("{}"))
                .toUnmodifiable());
        var signed = new AtomicReference<HttpRequest>();
        var endpoint = Endpoint.builder()
                .uri("https://mrap.accesspoint.s3-global.amazonaws.com")
                .addAuthScheme(EndpointAuthScheme.builder()
                        .authSchemeId("aws.auth#sigv4a")
                        .putProperty(EndpointAuthSchemeSettings.SIGNING_NAME, "s3")
                        .putProperty(EndpointAuthSchemeSettings.SIGNING_REGION_SET, List.of("*"))
                        .putProperty(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING, true)
                        .build())
                .build();

        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(MODEL)
                .protocol(new AwsJson1Protocol(SERVICE))
                .endpointResolver(EndpointResolver.staticEndpoint(endpoint))
                .putConfig(RegionSetting.REGION, "us-east-1")
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(SigV4Trait.ID)))
                .putSupportedAuthSchemes(new SigV4AuthScheme("sprockets"), new SigV4aAuthScheme("sprockets"))
                .addIdentityResolver(IdentityResolver.of(AwsCredentialsIdentity.create("AKID", "secret")))
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        signed.set((HttpRequest) hook.request());
                    }
                })
                .build();

        client.call("CreateSprocket");

        var headers = signed.get().headers();
        assertThat(headers.firstValue("x-amz-region-set")).isEqualTo("*");
        assertThat(headers.firstValue("x-amz-content-sha256")).isNotNull();
        assertThat(headers.firstValue("authorization"))
                .startsWith("AWS4-ECDSA-P256-SHA256 Credential=AKID/")
                .contains("/s3/aws4_request, SignedHeaders=");
    }
}
