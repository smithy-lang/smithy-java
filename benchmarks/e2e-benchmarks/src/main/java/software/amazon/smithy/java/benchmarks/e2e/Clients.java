/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.auth.scheme.s3express.CreateSessionCallback;
import software.amazon.smithy.java.aws.client.auth.scheme.s3express.S3ExpressContext;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.imds.ImdsCredentialProvider;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.e2e.s3.client.S3Client;
import software.amazon.smithy.java.benchmarks.e2e.s3.model.CreateSessionInput;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider;
import software.amazon.smithy.java.client.http.smithy.SmithyHttpClientTransport;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * Constructs the smithy-java-generated DynamoDB and S3 clients used by the benchmark.
 * Region comes from the benchmark runner; credentials come directly from the EC2 IMDSv2 endpoint.
 */
final class Clients {

    private static final IdentityResolver<AwsCredentialsIdentity> IMDS = buildImds();

    private Clients() {}

    /**
     * Apply a {@code -D<prop>=<bytes|auto>} socket-buffer knob: use the property value when set
     * (where {@code auto}/{@code -1} means "kernel autotune"), otherwise apply {@code defaultBytes}.
     * Pass {@code -1} as the default to leave the socket at kernel autotune when the property is unset.
     */
    private static void applyBufferProp(String prop, int defaultBytes, IntConsumer setter) {
        Integer value = parseBufferProp(prop);
        int bytes = value != null ? value : defaultBytes;
        // -1 == kernel autotune: leave the socket option unset.
        if (bytes != -1) {
            setter.accept(bytes);
        }
    }

    /**
     * Apply a TLS buffer-size knob: use {@code -D<prop>=<bytes>} when set, else {@code defaultBytes}.
     * Unlike the socket SO_*BUF knobs there is no "auto"/-1 form — these are concrete buffer
     * capacities, not a kernel-autotune toggle — so a value of -1 is rejected.
     */
    private static void applyTlsBufferProp(String prop, int defaultBytes, IntConsumer setter) {
        Integer value = parseBufferProp(prop);
        int bytes = value != null ? value : defaultBytes;
        if (bytes <= 0) {
            throw new IllegalArgumentException(prop + " must be a positive byte count: " + bytes);
        }
        setter.accept(bytes);
    }

    /**
     * Parse a {@code -D<prop>=<bytes|auto>} property: {@code -1} for "auto", null if unset.
     */
    private static Integer parseBufferProp(String prop) {
        var value = System.getProperty(prop);
        if (value == null) {
            return null;
        }
        var trimmed = value.trim().toLowerCase();
        return "auto".equals(trimmed) ? -1 : Integer.parseInt(trimmed);
    }

    private static int maxConnections() {
        return Integer.getInteger("e2e.maxconns", 1024);
    }

    /**
     * Returns the alternate transport selected via {@code -De2e.transport=...}, or null for the
     * default JDK HttpClient. Recognized values: {@code smithy}, {@code smithy-boringssl}.
     */
    private static ClientTransport<?, ?> selectTransport() {
        var name = System.getProperty("e2e.transport", "").trim().toLowerCase();
        return switch (name) {
            case "", "jdk" -> null;
            case "smithy" -> new SmithyHttpClientTransport(smithyPool(false));
            // Same smithy native transport, but TLS is driven by the BoringSSL (netty-tcnative)
            // SSLEngine instead of the JDK engine — keeps the cheaper AES-GCM without the Netty
            // pipeline. Falls back to the JDK provider if tcnative is unavailable on the host.
            case "smithy-boringssl" -> new SmithyHttpClientTransport(smithyPool(true));
            default -> throw new IllegalArgumentException(
                    "Unknown e2e.transport: '" + name
                            + "' (expected one of: jdk, smithy, smithy-boringssl)");
        };
    }

    /**
     * Build the smithy native transport's HTTP client. Shared by the {@code smithy} and
     * {@code smithy-boringssl} variants; the latter injects the BoringSSL SSLEngine factory.
     *
     * <p>Smithy HTTP client defaults to ENFORCE_HTTP_2 which fails on S3 (H1-only); AUTOMATIC also
     * fails (the pool routes HTTPS to the H2 manager, which refuses an ALPN result of "http/1.1").
     * Force ENFORCE_HTTP_1_1 so the pool routes to the H1 manager from the start. The pool's default
     * maxConnectionsPerRoute=20 throttles a single-bucket benchmark hard, so use the shared
     * -De2e.maxconns cap (default unbounded) for equal footing with netty.
     */
    private static HttpClient smithyPool(boolean boringSsl) {
        int maxConns = maxConnections();
        var builder = HttpClient.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(maxConns)
                .maxConnectionsPerRoute(maxConns);
        applyBufferProp("e2e.smithy.recvbuf", 1024 * 1024, builder::socketReceiveBufferSize);
        applyBufferProp("e2e.smithy.sendbuf", 1024 * 1024, builder::socketSendBufferSize);
        applyTlsBufferProp("e2e.smithy.tls.readbuf", 256 * 1024, builder::tlsReadBufferSize);
        applyTlsBufferProp("e2e.smithy.tls.writebuf", 256 * 1024, builder::tlsWriteBufferSize);
        // The epoll transport is selected automatically when the native library is available.
        if (boringSsl) {
            if (BoringSslTlsProvider.available()) {
                builder.tlsProvider(BoringSslTlsProvider.create(false));
            } else {
                System.err.println("smithy-boringssl requested but netty-tcnative unavailable; "
                        + "using JDK SSLEngine");
            }
        }
        return builder.build();
    }

    static DynamoDBClient dynamodb(String region) {
        var b = DynamoDBClient.builder()
                .putConfig(RegionSetting.REGION, region)
                .addIdentityResolver(IMDS);
        var transport = selectTransport();
        if (transport != null) {
            b.transport(transport);
        }
        return b.build();
    }

    static S3Client s3(String region) {
        // S3ExpressPlugin is an AutoClientPlugin discovered via ServiceLoader. It reads
        // CREATE_SESSION_CALLBACK from the builder's context and (when present) registers
        // S3ExpressAuthScheme + the bucket interceptor + disable-session-auth resolver. We
        // just have to provide the callback that can call createSession on the same client
        // we're configuring — chicken-and-egg resolved by an AtomicReference that gets
        // populated after build(). TODO: fix
        var clientRef = new AtomicReference<S3Client>();
        CreateSessionCallback createSession = (bucket, baseCreds) -> {
            S3Client client = clientRef.get();
            if (client == null) {
                throw new IllegalStateException("S3 client not yet initialized; cannot CreateSession");
            }
            var resp = client.createSession(CreateSessionInput.builder().bucket(bucket).build());
            var c = resp.getCredentials();
            return AwsCredentialsIdentity.create(
                    c.getAccessKeyId(),
                    c.getSecretAccessKey(),
                    c.getSessionToken(),
                    c.getExpiration());
        };
        var b = S3Client.builder()
                .putConfig(RegionSetting.REGION, region)
                .putConfig(S3ExpressContext.CREATE_SESSION_CALLBACK, createSession)
                .addIdentityResolver(IMDS);
        var transport = selectTransport();
        if (transport != null) {
            b.transport(transport);
        }
        S3Client client = b.build();
        clientRef.set(client);
        return client;
    }

    @SuppressWarnings("unchecked")
    private static IdentityResolver<AwsCredentialsIdentity> buildImds() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "e2e-imds-refresh");
            t.setDaemon(true);
            return t;
        });
        var provider = new ImdsCredentialProvider();
        var setup = ChainSetup.builder().executor(executor).build();
        setup.setCurrentProvider(provider);
        provider.setup(AwsCredentialsIdentity.class, setup);
        var resolvers = setup.resolvers();
        if (resolvers.isEmpty()) {
            throw new IllegalStateException("IMDS provider did not register a resolver");
        }
        return (IdentityResolver<AwsCredentialsIdentity>) resolvers.getFirst().resolver();
    }
}
