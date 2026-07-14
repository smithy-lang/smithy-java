/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Throwaway BASELINE probe (Arm B): observe what the smithy-java SDK actually does on the wire
 * for a REST-JSON Sprockets client, using ONLY a hand-written observing interceptor + a JUL log
 * capture. No inspector MCP. Prints findings to stdout.
 */
public class BaselineProbeTest {

    static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    static final Model MODEL = Model.assembler(BaselineProbeTest.class.getClassLoader())
            .addUnparsedModel("sprockets.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    @aws.auth#sigv4(name: "sprockets")
                    service Sprockets {
                        operations: [GetSprocket]
                    }

                    @http(method: "POST", uri: "/s")
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                    }
                    """)
            .discoverModels(BaselineProbeTest.class.getClassLoader())
            .assemble()
            .unwrap();

    /** Observing interceptor: counts attempts, records wire method/status, phase timings, faults. */
    static final class Probe implements ClientInterceptor {
        final AtomicInteger attempts = new AtomicInteger();
        long serializeStart, serializeMs = -1;
        long deserializeStart, deserializeMs = -1;
        String method, uri;
        final List<Integer> statuses = new ArrayList<>();
        volatile boolean forceFail;

        @Override
        public void readBeforeSerialization(InputHook<?, ?> hook) {
            serializeStart = System.nanoTime();
        }

        @Override
        public void readAfterSerialization(RequestHook<?, ?, ?> hook) {
            serializeMs = (System.nanoTime() - serializeStart) / 1_000_000L;
        }

        @Override
        public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
            attempts.incrementAndGet();
        }

        @Override
        public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
            if (hook.request() instanceof HttpRequest req) {
                method = req.method();
                uri = String.valueOf(req.uri());
            }
            if (forceFail) {
                // Force the logical call to fail on demand, independent of the primed backend.
                throw new IllegalStateException("BASELINE-PROBE-FORCED-FAILURE (client-side, not backend)");
            }
            return hook.request();
        }

        @Override
        public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
            if (hook.response() instanceof HttpResponse resp) {
                statuses.add(resp.statusCode());
            }
        }

        @Override
        public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
            deserializeStart = System.nanoTime();
        }

        @Override
        public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
            deserializeMs = (System.nanoTime() - deserializeStart) / 1_000_000L;
        }
    }

    /** Capture the SDK's own internal log lines (routed SLF4J -> JUL). */
    static final class LogCapture extends Handler {
        final List<String> lines = new ArrayList<>();
        private final SimpleFormatter formatter = new SimpleFormatter();

        @Override
        public void publish(LogRecord record) {
            lines.add(record.getLevel() + " [" + record.getLoggerName() + "] "
                    + formatter.formatMessage(record));
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    private DynamicClient buildClient(MockQueue queue, Probe probe) {
        var credentials = AwsBasicCredentials.create("access_key", "secret_key");
        return DynamicClient.builder()
                .model(MODEL)
                .serviceId(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .retryStrategy(zeroBackoffRetry())
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin((ClientConfig.Builder cfg) -> cfg.addInterceptor(probe))
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

    @Test
    public void probe() {
        // Turn on ALL levels so the SDK's TRACE/DEBUG lines reach our handler (SLF4J -> JUL).
        Logger root = Logger.getLogger("");
        Level prevRoot = root.getLevel();
        root.setLevel(Level.ALL);
        Logger pipelineLogger =
                Logger.getLogger("software.amazon.smithy.java.client.core.ClientPipeline");
        pipelineLogger.setLevel(Level.ALL);
        LogCapture capture = new LogCapture();
        capture.setLevel(Level.ALL);
        root.addHandler(capture);

        try {
            // Q1: successful call.
            var okQueue = new MockQueue();
            okQueue.enqueue(okResponse("q1"));
            var okProbe = new Probe();
            var okOut = buildClient(okQueue, okProbe)
                    .call("GetSprocket", Document.of(Map.of("id", Document.of("q1"))));
            System.out.println("=== Q1 OK CALL ===");
            System.out.println("returnedId=" + okOut.getMember("id").asString());
            System.out.println("wireMethod=" + okProbe.method + " wireUri=" + okProbe.uri);
            System.out.println("statuses=" + okProbe.statuses);
            System.out.println("attempts=" + okProbe.attempts.get());
            System.out.println("serializeMs=" + okProbe.serializeMs + " deserializeMs=" + okProbe.deserializeMs);

            // Q2: retry (429 then 200).
            var retryQueue = new MockQueue();
            retryQueue.enqueue(errorResponse(429, "InvalidSprocketId"));
            retryQueue.enqueue(okResponse("q2"));
            var retryProbe = new Probe();
            var retryOut = buildClient(retryQueue, retryProbe)
                    .call("GetSprocket", Document.of(Map.of("id", Document.of("q2"))));
            System.out.println("=== Q2 RETRY CALL (429 then 200) ===");
            System.out.println("returnedId=" + retryOut.getMember("id").asString());
            System.out.println("statuses=" + retryProbe.statuses);
            System.out.println("attempts=" + retryProbe.attempts.get());

            // Q4: force a failure independent of the (primed-to-succeed) backend.
            var failQueue = new MockQueue();
            failQueue.enqueue(okResponse("q4")); // backend WOULD succeed
            var failProbe = new Probe();
            failProbe.forceFail = true;
            String outcome;
            String errType = null;
            String errMsg = null;
            try {
                buildClient(failQueue, failProbe)
                        .call("GetSprocket", Document.of(Map.of("id", Document.of("q4"))));
                outcome = "success";
            } catch (RuntimeException e) {
                outcome = "error";
                errType = e.getClass().getName();
                errMsg = e.getMessage();
                Throwable c = e.getCause();
                while (c != null) {
                    if (c.getMessage() != null && c.getMessage().contains("BASELINE-PROBE-FORCED-FAILURE")) {
                        errMsg = c.getMessage();
                        break;
                    }
                    c = c.getCause();
                }
            }
            System.out.println("=== Q4 FORCED FAILURE ===");
            System.out.println("outcome=" + outcome);
            System.out.println("errorType=" + errType);
            System.out.println("errorMessage=" + errMsg);
            System.out.println("attemptsBeforeFail=" + failProbe.attempts.get()
                    + " statusesFromBackend=" + failProbe.statuses);

            // Q3: SDK internal log lines emitted during the calls.
            System.out.println("=== Q3 SDK INTERNAL LOG LINES ===");
            for (String line : capture.lines) {
                if (line.contains("smithy.java.client")) {
                    System.out.println(line);
                }
            }
        } finally {
            root.removeHandler(capture);
            root.setLevel(prevRoot);
        }
    }

    private static RetryStrategy zeroBackoffRetry() {
        return new RetryStrategy() {
            @Override
            public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                return new AcquireInitialTokenResponse(new Token(), Duration.ZERO);
            }

            @Override
            public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                return new RefreshRetryTokenResponse(new Token(), Duration.ZERO);
            }

            @Override
            public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                return new RecordSuccessResponse(request.token());
            }

            @Override
            public int maxAttempts() {
                return 3;
            }

            @Override
            public Builder toBuilder() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class Token implements RetryToken {}
}
