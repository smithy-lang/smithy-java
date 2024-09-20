/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.io.uri.QueryStringBuilder;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SigV4SignerTrials {
    private static final AwsCredentialsIdentity TEST_IDENTITY = AwsCredentialsIdentity.create(
        "access-key",
        "secret-key"
    );
    private static final AuthProperties TEST_PROPERTIES = AuthProperties.builder()
        .put(SigV4Settings.SIGNING_NAME, "service")
        .put(SigV4Settings.REGION, "us-east-1")
        .build();
    private static final Map<String, SmithyHttpRequest> CASES = Map.ofEntries(
        Map.entry(
            "put_no_headers_no_query_no_body",
            parsePostRequest(Collections.emptyMap(), Collections.emptyMap(), null)
        ),
        Map.entry(
            "put_with_headers_no_query_with_body",
            parsePostRequest(Collections.emptyMap(), Collections.emptyMap(), "body of the messsssssssssssssage")
        ),
        Map.entry(
            "put_no_headers_with_query_no_body",
            parsePostRequest(Map.of("A", "b", "key", "value"), Collections.emptyMap(), null)
        ),
        Map.entry(
            "put_with_headers_no_query_no_body",
            parsePostRequest(
                Collections.emptyMap(),
                Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                null
            )
        ),
        Map.entry(
            "put_with_headers_with_query_with_body",
            parsePostRequest(
                Map.of("A", "b", "key", "value"),
                Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                "body of the messsssssssssssssage"
            )
        )
    );

    @Param(
        {
            "put_no_headers_no_query_no_body",
            "put_with_headers_no_query_with_body",
            "put_no_headers_with_query_no_body",
            "put_with_headers_no_query_no_body",
            "put_with_headers_with_query_with_body"
        }
    )
    private String testName;
    private SmithyHttpRequest request;
    private Signer<SmithyHttpRequest, AwsCredentialsIdentity> signer;

    @Setup
    public void setup() throws Exception {
        request = CASES.get(testName);
        signer = SigV4Signer.INSTANCE;
    }

    @Benchmark
    public void sign() throws IOException, ExecutionException, InterruptedException {
        signer.sign(request, TEST_IDENTITY, TEST_PROPERTIES).get();
    }

    private static SmithyHttpRequest parsePostRequest(
        Map<String, String> queryParameters,
        Map<String, List<String>> headers,
        String body
    ) {
        var httpHeaders = HttpHeaders.of(headers, (k, v) -> true);
        var uriString = "http://example.com";
        if (!queryParameters.isEmpty()) {
            var queryBuilder = new QueryStringBuilder();
            queryParameters.forEach(queryBuilder::put);
            uriString += "?" + queryBuilder;
        }
        return SmithyHttpRequest.builder()
            .method("POST")
            .httpVersion(SmithyHttpVersion.HTTP_1_1)
            .uri(URI.create(uriString))
            .headers(httpHeaders)
            .body(body != null ? DataStream.ofBytes(body.getBytes(StandardCharsets.UTF_8)) : null)
            .build();
    }
}
