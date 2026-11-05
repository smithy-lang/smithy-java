/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.QueryStringBuilder;
import software.amazon.smithy.java.io.uri.SmithyUri;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SigV4SignerTrials {

    private static final AwsCredentialsIdentity[] IDENTITIES = {
            AwsCredentialsIdentity.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"),
            AwsCredentialsIdentity.create("AKIDROTATED", "rotated-secret-key", "AQoDYXdzEJr/session/token")};

    private static final Context TEST_PROPERTIES;
    static {
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, "service");
        ctx.put(SigV4Settings.REGION, "us-east-1");
        TEST_PROPERTIES = Context.unmodifiableView(ctx);
    }

    private static final Map<String, HttpRequest> CASES = Map.ofEntries(
            Map.entry(
                    "post_no_headers_no_query_no_body",
                    request(Collections.emptyMap(), Collections.emptyMap(), 0)),
            Map.entry(
                    "post_with_headers_with_query_small_body",
                    request(
                            Map.of("A", "b", "key", "value"),
                            Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                            32)),
            Map.entry(
                    "put_with_headers_64k_body",
                    request(Collections.emptyMap(),
                            Map.of("content-type", List.of("application/octet-stream")),
                            64 * 1024)));

    @Param({
            "post_no_headers_no_query_no_body",
            "post_with_headers_with_query_small_body",
            "put_with_headers_64k_body"
    })
    private String testName;

    @Param({"no", "yes"})
    private String rotateCredentials;

    @Param({"no", "yes"})
    private String useAccpParam;

    @Param({"default", "builtin"})
    private String hashProviders;

    private boolean skipTest;
    private boolean rotate;
    private HttpRequest request;

    @State(Scope.Thread)
    public static class ThreadState {
        int calls;
    }

    @Setup
    public void setup() throws Exception {
        // Set the property before SigningResources initializes in this fork.
        System.setProperty(SigningResources.PREFER_BUILTIN_HASH_PROVIDERS_PROPERTY,
                Boolean.toString(hashProviders.equals("builtin")));
        boolean useAccp = useAccpParam.equals("yes");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        skipTest = useAccp && isWindows;
        if (!isWindows) {
            var clazz = Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");
            if (useAccp) {
                clazz.getMethod("install").invoke(null);
            } else {
                Security.removeProvider(clazz.getName());
            }
        }
        rotate = rotateCredentials.equals("yes");
        request = CASES.get(testName);
    }

    @Benchmark
    public SignResult<HttpRequest> sign(ThreadState thread) {
        return signOnce(thread);
    }

    @Benchmark
    @Threads(4)
    public SignResult<HttpRequest> signConcurrently(ThreadState thread) {
        return signOnce(thread);
    }

    private SignResult<HttpRequest> signOnce(ThreadState thread) {
        if (skipTest) {
            return null;
        }
        var identity = rotate ? IDENTITIES[(thread.calls++) & 1] : IDENTITIES[0];
        try (var signer = SigV4Signer.create()) {
            return signer.sign(request, identity, TEST_PROPERTIES);
        }
    }

    static HttpRequest request(Map<String, String> queryParameters, Map<String, List<String>> headers, int bodySize) {
        var uriString = "https://example.amazonaws.com/path/to/resource";
        if (!queryParameters.isEmpty()) {
            var queryBuilder = new QueryStringBuilder();
            queryParameters.forEach(queryBuilder::add);
            uriString += "?" + queryBuilder;
        }
        var builder = HttpRequest.create()
                .setMethod(bodySize > 1024 ? "PUT" : "POST")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(SmithyUri.of(uriString))
                .setHeaders(HttpHeaders.of(headers));
        if (bodySize > 0) {
            byte[] body = new byte[bodySize];
            for (int i = 0; i < body.length; i++) {
                body[i] = (byte) ('a' + (i % 26));
            }
            builder.setBody(DataStream.ofBytes(body));
        }
        return builder.toUnmodifiable();
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
