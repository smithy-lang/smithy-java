/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

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
import software.amazon.smithy.java.http.api.HttpRequest;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SigV4aSignerTrials {

    private static final AwsCredentialsIdentity[] IDENTITIES = {
            AwsCredentialsIdentity.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"),
            AwsCredentialsIdentity.create("AKIDROTATED", "rotated-secret-key", "AQoDYXdzEJr/session/token")};

    private static final Context TEST_PROPERTIES;
    static {
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, "service");
        ctx.put(SigV4Settings.REGION, "us-east-1");
        ctx.put(SigV4aSettings.SIGNING_REGION_SET, List.of("*"));
        TEST_PROPERTIES = Context.unmodifiableView(ctx);
    }

    private static final Map<String, HttpRequest> CASES = Map.ofEntries(
            Map.entry(
                    "post_no_headers_no_query_no_body",
                    SigV4SignerTrials.request(Collections.emptyMap(), Collections.emptyMap(), 0)),
            Map.entry(
                    "post_with_headers_with_query_small_body",
                    SigV4SignerTrials.request(
                            Map.of("A", "b", "key", "value"),
                            Map.of("x-test", List.of("value"), "x-other", List.of("stuff")),
                            32)),
            Map.entry(
                    "put_with_headers_64k_body",
                    SigV4SignerTrials.request(
                            Collections.emptyMap(),
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

    private boolean skipTest;
    private boolean rotate;
    private HttpRequest request;

    @State(Scope.Thread)
    public static class ThreadState {
        int calls;
    }

    @Setup
    public void setup() throws Exception {
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
        try (var signer = SigV4aSigner.create()) {
            return signer.sign(request, identity, TEST_PROPERTIES);
        }
    }
}
