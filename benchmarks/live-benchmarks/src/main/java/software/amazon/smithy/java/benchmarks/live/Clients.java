/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.live;

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
import software.amazon.smithy.java.benchmarks.live.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.live.s3.client.S3Client;
import software.amazon.smithy.java.benchmarks.live.s3.model.CreateSessionInput;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider;
import software.amazon.smithy.java.client.http.smithy.SmithyHttpClientTransport;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

final class Clients {

    private static final IdentityResolver<AwsCredentialsIdentity> IMDS = buildImds();

    private Clients() {}

    /** A value of auto or -1 leaves socket buffer sizing to the kernel. */
    private static void applyBufferProp(String prop, int defaultBytes, IntConsumer setter) {
        Integer value = parseBufferProp(prop);
        int bytes = value != null ? value : defaultBytes;
        if (bytes != -1) {
            setter.accept(bytes);
        }
    }

    private static void applyTlsBufferProp(String prop, int defaultBytes, IntConsumer setter) {
        Integer value = parseBufferProp(prop);
        int bytes = value != null ? value : defaultBytes;
        if (bytes <= 0) {
            throw new IllegalArgumentException(prop + " must be a positive byte count: " + bytes);
        }
        setter.accept(bytes);
    }

    private static Integer parseBufferProp(String prop) {
        var value = System.getProperty(prop);
        if (value == null) {
            return null;
        }
        var trimmed = value.trim().toLowerCase();
        return "auto".equals(trimmed) ? -1 : Integer.parseInt(trimmed);
    }

    private static int maxConnections() {
        return Integer.getInteger("live.maxconns", 1024);
    }

    private static ClientTransport<?, ?> selectTransport() {
        var name = System.getProperty("live.transport", "").trim().toLowerCase();
        return switch (name) {
            case "", "jdk" -> null;
            case "smithy" -> new SmithyHttpClientTransport(smithyPool(false));
            case "smithy-boringssl" -> new SmithyHttpClientTransport(smithyPool(true));
            default -> throw new IllegalArgumentException(
                    "Unknown live.transport: '" + name
                            + "' (expected one of: jdk, smithy, smithy-boringssl)");
        };
    }

    /** Force HTTP/1.1 for S3 and use the shared connection limit to avoid pool throttling. */
    private static HttpClient smithyPool(boolean boringSsl) {
        int maxConns = maxConnections();
        var builder = HttpClient.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(maxConns)
                .maxConnectionsPerRoute(maxConns);
        applyBufferProp("live.smithy.recvbuf", 1024 * 1024, builder::socketReceiveBufferSize);
        applyBufferProp("live.smithy.sendbuf", 1024 * 1024, builder::socketSendBufferSize);
        applyTlsBufferProp("live.smithy.tls.readbuf", 256 * 1024, builder::tlsReadBufferSize);
        applyTlsBufferProp("live.smithy.tls.writebuf", 256 * 1024, builder::tlsWriteBufferSize);
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
        // The session callback needs the client after construction.
        // TODO: Fix the callback's dependency on client construction.
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
            Thread t = new Thread(r, "live-imds-refresh");
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
