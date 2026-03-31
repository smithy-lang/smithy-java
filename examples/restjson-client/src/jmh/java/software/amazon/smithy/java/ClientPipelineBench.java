/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockedResult;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.example.restjson.client.PersonDirectoryClient;
import software.amazon.smithy.java.example.restjson.model.PutPersonInput;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Benchmarks the full client pipeline: serialization, URI construction,
 * endpoint resolution, setServiceEndpoint, and signing.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ClientPipelineBench {

    private PersonDirectoryClient client;
    private PutPersonInput input;

    private static final byte[] RESPONSE_BODY =
            "{\"name\":\"Alice\",\"favoriteColor\":\"blue\",\"Age\":30}".getBytes(StandardCharsets.UTF_8);

    @Setup
    public void setup() {
        var mockResponse = HttpResponse.builder()
                .statusCode(200)
                .headers(HttpHeaders.of(Map.of("content-type", List.of("application/json"))))
                .body(DataStream.ofBytes(RESPONSE_BODY, "application/json"))
                .build();

        var mockPlugin = MockPlugin.builder()
                        .trackRequests(false)
                        .addMatcher(req -> new MockedResult.Response(mockResponse))
                        .build();

        client = PersonDirectoryClient.builder()
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com/v1"))
                .addPlugin(mockPlugin)
                .build();

        input = PutPersonInput.builder()
                .name("Alice")
                .favoriteColor("blue")
                .age(30)
                .build();
    }

    @Benchmark
    public Object putPerson() {
        return client.putPerson(input);
    }
}
