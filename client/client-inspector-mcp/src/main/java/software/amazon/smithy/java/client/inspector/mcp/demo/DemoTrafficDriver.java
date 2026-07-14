/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp.demo;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.StandardRetryStrategy;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Fires a <strong>fixed, predetermined</strong> sequence of real REST-JSON {@link DynamicClient}
 * calls for a contrived "Sprockets" service against a mock backend, with the inspector plugin
 * attached. This stands in for the developer's own application: it makes genuine SDK calls —
 * serialization, signing, transmit, retries — that the agent then inspects.
 *
 * <p>Crucially, this is <em>not</em> an MCP tool. The agent cannot choose what traffic is produced;
 * it runs once before the MCP server starts serving, and the agent's job is to characterize what
 * already happened. This removes the "agent scripts its own answer" circularity that a
 * traffic-generation tool would introduce.
 */
final class DemoTrafficDriver {

    static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    static final Model MODEL = Model.assembler(DemoTrafficDriver.class.getClassLoader())
            .addUnparsedModel("sprockets.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    @aws.auth#sigv4(name: "sprockets")
                    service Sprockets {
                        operations: [GetSprocket]
                    }

                    // @readonly makes GetSprocket safe to retry: ApplyModelRetryInfoPlugin (a
                    // default plugin) upgrades a failed attempt to isRetrySafe=YES for readonly
                    // operations, so the real StandardRetryStrategy retries it.
                    @http(method: "POST", uri: "/s")
                    @readonly
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                    }
                    """)
            .discoverModels(DemoTrafficDriver.class.getClassLoader())
            .assemble()
            .unwrap();

    private final ClientPlugin inspectorPlugin;

    DemoTrafficDriver(ClientPlugin inspectorPlugin) {
        this.inspectorPlugin = inspectorPlugin;
    }

    /**
     * Runs a fixed traffic mix the agent cannot see or influence: a clean success, a transient
     * 429-then-200 (retried by the real strategy), and a persistent 500 (surfaces as an error).
     * The agent characterizes these after the fact via the inspector tools.
     */
    void runFixedTraffic() {
        // 1) A straightforward success.
        fire(okQueue("alpha"), "alpha");
        // 2) A transient failure that the SDK transparently retries and recovers from.
        var retryQueue = new MockQueue();
        retryQueue.enqueue(errorResponse(429, "InvalidSprocketId"));
        retryQueue.enqueue(okResponse("bravo"));
        fire(retryQueue, "bravo");
        // 3) A hard, persistent failure the SDK gives up on.
        var errorQueue = new MockQueue();
        errorQueue.enqueue(errorResponse(500, "InternalServerError"));
        errorQueue.enqueue(errorResponse(500, "InternalServerError"));
        errorQueue.enqueue(errorResponse(500, "InternalServerError"));
        fire(errorQueue, "charlie");
    }

    private void fire(MockQueue queue, String id) {
        try {
            buildClient(queue).call("GetSprocket", Document.of(Map.of("id", Document.of(id))));
        } catch (RuntimeException e) {
            // Expected for the failure case; the inspector records it. Swallow so the driver
            // completes and the server can serve inspection of all three calls.
        }
    }

    private MockQueue okQueue(String id) {
        var queue = new MockQueue();
        queue.enqueue(okResponse(id));
        return queue;
    }

    private DynamicClient buildClient(MockQueue queue) {
        var credentials = AwsBasicCredentials.create("access_key", "secret_key");
        return DynamicClient.builder()
                .model(MODEL)
                .serviceId(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                // The SDK's real retry strategy — a 429 retries only because GetSprocket is
                // @readonly and ApplyModelRetryInfoPlugin marks it isRetrySafe=YES. Backoff is
                // shortened only to keep the demo snappy; the retry decision is the real one.
                .retryStrategy(StandardRetryStrategy.builder()
                        .maxAttempts(3)
                        .backoffBaseDelay(Duration.ofMillis(1))
                        .build())
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin(inspectorPlugin)
                .endpointResolver(EndpointResolver.staticEndpoint(URI.create("http://localhost")))
                .putConfig(RegionSetting.REGION, "us-west-2")
                .putSupportedAuthSchemes(new SigV4AuthScheme("sprockets"))
                .authSchemeResolver(AuthSchemeResolver.DEFAULT)
                .addIdentityResolver(new SdkCredentialsResolver(StaticCredentialsProvider.create(credentials)))
                .build();
    }

    private static HttpResponse okResponse(String id) {
        return HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"" + id + "\"}"))
                .setStatusCode(200)
                .toUnmodifiable();
    }

    private static HttpResponse errorResponse(int status, String type) {
        return HttpResponse.create()
                .setBody(DataStream.ofString("{\"__type\":\"" + type + "\"}"))
                .setStatusCode(status)
                .toUnmodifiable();
    }
}
