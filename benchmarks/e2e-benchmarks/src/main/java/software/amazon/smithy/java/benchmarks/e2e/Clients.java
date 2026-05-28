/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
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
     * Returns the alternate transport selected via {@code -De2e.transport=...}, or null for the
     * default JDK HttpClient. Recognized values: {@code netty}, {@code smithy}.
     */
    private static ClientTransport<?, ?> selectTransport() {
        var name = System.getProperty("e2e.transport", "").trim().toLowerCase();
        return switch (name) {
            case "", "jdk" -> null;
            case "netty" -> new NettyHttpClientTransport();
            case "apache" -> {
                var cfg = new ApacheHttpTransportConfig()
                        .maxConnectionsPerHost(512)
                        .ioThreads(Runtime.getRuntime().availableProcessors());
                yield new ApacheHttpClientTransport(cfg);
            }
            case "smithy" -> {
                // Smithy HTTP client defaults to ENFORCE_HTTP_2 which fails on S3 (H1-only).
                // AUTOMATIC also fails: the pool routes HTTPS routes to the H2 manager, which
                // refuses an ALPN result of "http/1.1". Force ENFORCE_HTTP_1_1 so the pool
                // routes to the H1 manager from the start.
                //
                // The pool defaults to maxConnectionsPerRoute=20 which throttles us hard at
                // higher concurrency since the benchmark targets a single bucket (= one route).
                // Bump both caps to match the benchmark's max in-flight count plus headroom.
                var pool = HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxTotalConnections(512)
                        .maxConnectionsPerRoute(512)
                        .build();
                var http = HttpClient.builder().connectionPool(pool).build();
                yield new SmithyHttpClientTransport(http);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown e2e.transport: '" + name + "' (expected one of: jdk, netty, smithy, apache)");
        };
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
