/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Security;
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
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.QueryStringBuilder;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SigV4SignerTrials {
    private static final AwsCredentialsIdentity TEST_IDENTITY = AwsCredentialsIdentity.create(
            "access-key",
            "secret-key");

    private static final Context TEST_PROPERTIES;
    static {
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, "service");
        ctx.put(SigV4Settings.REGION, "us-east-1");
        TEST_PROPERTIES = Context.unmodifiableView(ctx);
    }

    private static final Map<String, HttpRequest> CASES = Map.ofEntries(
            Map.entry(
                    "put_no_headers_no_query_no_body",
                    parsePostRequest(Collections.emptyMap(), Collections.emptyMap(), null)),
            Map.entry(
                    "put_with_headers_no_query_with_body",
                    parsePostRequest(Collections.emptyMap(),
                            Collections.emptyMap(),
                            "body of the messsssssssssssssage")),
            Map.entry(
                    "put_no_headers_with_query_no_body",
                    parsePostRequest(Map.of("A", "b", "key", "value"), Collections.emptyMap(), null)),
            Map.entry(
                    "put_with_headers_no_query_no_body",
                    parsePostRequest(
                            Collections.emptyMap(),
                            Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                            null)),
            Map.entry(
                    "put_with_headers_with_query_with_body",
                    parsePostRequest(
                            Map.of("A", "b", "key", "value"),
                            Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                            "body of the messsssssssssssssage")));

    @Param({
            "put_no_headers_no_query_no_body",
            "put_with_headers_no_query_with_body",
            "put_no_headers_with_query_no_body",
            "put_with_headers_no_query_no_body",
            "put_with_headers_with_query_with_body"
    })
    private String testName;

    @Param({"yes", "no"})
    private String useAccpParam;

    private boolean isWindows;
    private boolean useAccp;
    private boolean skipTest;
    private HttpRequest request;
    private Signer<HttpRequest, AwsCredentialsIdentity> signer;

    @Setup
    public void setup() throws Exception {
        useAccp = useAccpParam.equals("yes");
        isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        skipTest = useAccp && isWindows;

        if (!isWindows) {
            var clazz = Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");
            if (useAccp) {
                clazz.getMethod("install").invoke(null);
            } else {
                Security.removeProvider(clazz.getName());
            }
        }

        request = CASES.get(testName);
        signer = SigV4Signer.create();
    }

    @Benchmark
    public void sign() throws IOException, ExecutionException, InterruptedException {
        if (!skipTest) {
            signer.sign(request, TEST_IDENTITY, TEST_PROPERTIES).get();
        } else {
            System.out.println("Skipping benchmark on Windows");
        }
    }

    private static HttpRequest parsePostRequest(
            Map<String, String> queryParameters,
            Map<String, List<String>> headers,
            String body
    ) {
        var httpHeaders = HttpHeaders.of(headers);
        var uriString = "http://example.com";
        if (!queryParameters.isEmpty()) {
            var queryBuilder = new QueryStringBuilder();
            queryParameters.forEach(queryBuilder::add);
            uriString += "?" + queryBuilder;
        }
        return HttpRequest.builder()
                .method("POST")
                .httpVersion(HttpVersion.HTTP_1_1)
                .uri(URI.create(uriString))
                .headers(httpHeaders)
                .body(body != null ? DataStream.ofBytes(body.getBytes(StandardCharsets.UTF_8)) : null)
                .build();
    }
}
