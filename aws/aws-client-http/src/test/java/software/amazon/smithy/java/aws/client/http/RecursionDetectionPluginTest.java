/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

public class RecursionDetectionPluginTest {
    @Test
    public void doesNothingWhenHeaderIsMissing() {
        // Set the trace to null for testing.
        var recursionDetectionPlugin = new RecursionDetectionPlugin(null);
        var headers = getSentHeaders(recursionDetectionPlugin);

        assertThat(headers.allValues("x-amzn-trace-id"), empty());
    }

    @Test
    public void setsHeaderUsingEnvvar() {
        var recursionDetectionPlugin = new RecursionDetectionPlugin("hello");
        var headers = getSentHeaders(recursionDetectionPlugin);

        assertThat(headers.allValues("x-amzn-trace-id"), contains("hello"));
    }

    private HttpHeaders getSentHeaders(ClientPlugin recursionDetectionPlugin) {
        return getSentHeaders(recursionDetectionPlugin, null);
    }

    private HttpHeaders getSentHeaders(ClientPlugin recursionDetectionPlugin, ClientInterceptor interceptor) {
        var mockQueue = new MockQueue();
        mockQueue.enqueue(HttpResponse.builder().statusCode(200).body(DataStream.ofString("{\"id\":\"1\"}")).build());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var builder = DynamicClient.builder()
            .service(TestHarness.SERVICE)
            .model(TestHarness.MODEL)
            .protocol(new AwsJson1Protocol(TestHarness.SERVICE))
            .addPlugin(mock)
            .addPlugin(recursionDetectionPlugin)
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .addPlugin(new AmzSdkRequestPlugin());

        if (interceptor != null) {
            builder.addInterceptor(interceptor);
        }

        var client = builder.build();
        client.call("CreateSprocket");

        return mock.getRequests().get(0).request().headers();
    }

    @Test
    public void doesNotReplaceExistingValue() {
        var recursionDetectionPlugin = new RecursionDetectionPlugin("foo");
        var headers = getSentHeaders(recursionDetectionPlugin, new ClientInterceptor() {
            @Override
            public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
                return hook.mapRequest(HttpRequest.class, h -> {
                    return h.request().toBuilder().withReplacedHeader("x-amzn-trace-id", List.of("hi")).build();
                });
            }
        });

        assertThat(headers.allValues("x-amzn-trace-id"), contains("hi"));
    }
}
