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
import software.amazon.smithy.java.aws.client.auth.scheme.s3express.S3ExpressIdentity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.imds.ImdsCredentialProvider;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.e2e.s3.client.S3Client;
import software.amazon.smithy.java.benchmarks.e2e.s3.model.CreateSessionInput;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.apache.ApacheHttpClientTransport;
import software.amazon.smithy.java.client.http.apache.ApacheHttpTransportConfig;
import software.amazon.smithy.java.client.http.apache.classic.ApacheClassicHttpClientTransport;
import software.amazon.smithy.java.client.http.boringssl.BoringSslEngineFactory;
import software.amazon.smithy.java.client.http.crt.CrtHttpClientTransport;
import software.amazon.smithy.java.client.http.crt.CrtHttpTransportConfig;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.NettyHttpTransportConfig;
import software.amazon.smithy.java.client.http.smithy.SmithyHttpClientTransport;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * Constructs the smithy-java-generated DynamoDB and S3 clients used by the benchmark.
 * Region comes from the workload's actionConfig; credentials come directly from the EC2 IMDSv2 endpoint.
 */
final class Clients {

    private static final IdentityResolver<AwsCredentialsIdentity> IMDS = buildImds();

    private Clients() {}

    /**
     * Apply a {@code -D<prop>=<bytes|auto>} system property to a setter that accepts an int
     * (with {@code -1} meaning "kernel autotune").
     */
    private static void applyBufferProp(String prop, IntConsumer setter) {
        Integer value = parseBufferProp(prop);
        if (value != null) {
            setter.accept(value);
        }
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
     * default JDK HttpClient. Recognized values: {@code netty}, {@code smithy}.
     */
    private static ClientTransport<?, ?> selectTransport() {
        var name = System.getProperty("e2e.transport", "").trim().toLowerCase();
        return switch (name) {
            case "", "jdk" -> null;
            case "netty" -> {
                var cfg = new NettyHttpTransportConfig()
                        .maxConnectionsPerHost(maxConnections());
                yield new NettyHttpClientTransport(cfg);
            }
            case "apache" -> {
                var cfg = new ApacheHttpTransportConfig()
                        .maxConnectionsPerHost(512)
                        .ioThreads(Runtime.getRuntime().availableProcessors());
                yield new ApacheHttpClientTransport(cfg);
            }
            case "apache-classic" -> new ApacheClassicHttpClientTransport(512, 512);
            case "crt" -> {
                var cfg = new CrtHttpTransportConfig()
                        .maxConnectionsPerHost(512);
                yield new CrtHttpClientTransport(cfg);
            }
            case "smithy" -> new SmithyHttpClientTransport(smithyPool(false));
            // Same smithy native transport, but TLS is driven by the BoringSSL (netty-tcnative)
            // SSLEngine instead of the JDK engine — keeps the cheaper AES-GCM without the Netty
            // pipeline. Falls back to the JDK provider if tcnative is unavailable on the host.
            case "smithy-boringssl" -> new SmithyHttpClientTransport(smithyPool(true));
            default -> throw new IllegalArgumentException(
                    "Unknown e2e.transport: '" + name
                            + "' (expected one of: jdk, netty, smithy, smithy-boringssl, apache, apache-classic, crt)");
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
        var poolBuilder = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(maxConns)
                .maxConnectionsPerRoute(maxConns);
        // -De2e.smithy.recvbuf=<bytes|auto>; -De2e.smithy.sendbuf=<bytes|auto>. "auto" maps to -1.
        applyBufferProp("e2e.smithy.recvbuf", poolBuilder::socketReceiveBufferSize);
        applyBufferProp("e2e.smithy.sendbuf", poolBuilder::socketSendBufferSize);
        if (boringSsl) {
            if (BoringSslEngineFactory.isAvailable()) {
                poolBuilder.sslEngineFactory(BoringSslEngineFactory.create(false));
            } else {
                System.err.println("smithy-boringssl requested but netty-tcnative unavailable; "
                        + "using JDK SSLEngine");
            }
        }
        return HttpClient.builder().connectionPool(poolBuilder.build()).build();
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
            return S3ExpressIdentity.create(
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
