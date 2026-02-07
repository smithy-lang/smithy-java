/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.metrics.otel;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static software.amazon.smithy.java.client.metrics.otel.OperationMetricsInterceptor.EXCEPTION_TYPE;
import static software.amazon.smithy.java.client.metrics.otel.OperationMetricsInterceptor.RPC_METHOD;
import static software.amazon.smithy.java.client.metrics.otel.OperationMetricsInterceptor.RPC_SERVICE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
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

class OperationMetricsPluginTest {
    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    @aws.auth#sigv4(name:"sprockets")
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
            .discoverModels()
            .assemble()
            .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    private InMemoryMetricReader metricReader;
    private OpenTelemetry openTelemetry;
    private OperationMetricsPlugin metricsPlugin;

    @BeforeEach
    void setUp() {
        // Create an in-memory metric reader
        metricReader = InMemoryMetricReader.create();

        // Build the SDK with the in-memory reader
        var meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        // Create the open telemetry instance with the configured SDK
        openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();

        // Create the plugin
        metricsPlugin = new OperationMetricsPlugin(openTelemetry);
    }

    @Test
    public void recordsTheExpectedMetrics() throws URISyntaxException {
        // Arrange
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.builder()
                .body(DataStream.ofString("{\"id\":\"10\"}"))
                .statusCode(200)
                .build());
        var client = createClient(queue);

        // Act
        client.call("GetSprocket", Map.of("id", "10"));

        // Assert
        var metrics = metricReader.collectAllMetrics();
        var name2data = metrics.stream()
                .collect(Collectors.toMap(MetricData::getName, Function.identity()));
        var expectedMetrics = mapOfExpectedMetrics();
        for (var kvp : name2data.entrySet()) {
            var expectation = expectedMetrics.get(kvp.getKey());
            assertNotNull(expectation);
            assertEquals(expectation.dataType, kvp.getValue().getType());
            assertEquals(expectation.unit, kvp.getValue().getUnit());
            assertAttributes(kvp.getValue());
        }
    }

    @Test
    public void recordsRetryAttempts() throws URISyntaxException {
        // Arrange
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.builder()
                .body(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                .statusCode(429)
                .build());
        queue.enqueue(HttpResponse.builder()
                .body(DataStream.ofString("{\"id\":\"10\"}"))
                .statusCode(200)
                .build());
        var client = createClient(queue);

        // Act
        client.call("GetSprocket", Map.of("id", "10"));

        // Assert
        var metrics = metricReader.collectAllMetrics();
        var name2data = metrics.stream()
                .collect(Collectors.toMap(MetricData::getName, Function.identity()));
        var expectedMetrics = mapOfExpectedMetrics();
        for (var kvp : name2data.entrySet()) {
            var expectation = expectedMetrics.get(kvp.getKey());
            assertNotNull(expectation);
            assertEquals(expectation.dataType, kvp.getValue().getType(), kvp.getKey());
            assertEquals(expectation.unit, kvp.getValue().getUnit(), kvp.getKey());
            assertAttributes(kvp.getValue());
        }
        // assert attempts
        var attempts = name2data.get(OperationMetrics.ATTEMPTS);
        var attemptsPoints = attempts.getData().getPoints().stream().toList();
        assertEquals(1, attemptsPoints.size());
        var attemptPoint = attemptsPoints.get(0);
        assertInstanceOf(LongPointData.class, attemptPoint);
        var attemptLongValue = ((LongPointData) attemptPoint).getValue();
        assertEquals(2, attemptLongValue);
        // assert errors
        var errors = name2data.get(OperationMetrics.ERRORS);
        var errorsPoints = errors.getData().getPoints().stream().toList();
        assertEquals(1, errorsPoints.size());
        var errorPoint = errorsPoints.get(0);
        assertInstanceOf(LongPointData.class, errorPoint);
        var errorLongValue = ((LongPointData) errorPoint).getValue();
        assertEquals(1, errorLongValue);
        String value = errorPoint.getAttributes().get(EXCEPTION_TYPE);
        assertEquals("software.amazon.smithy.java.core.error.CallException", value);
    }

    private void assertAttributes(MetricData data) {
        for (var point : data.getData().getPoints()) {
            var attributes = point.getAttributes();
            assertEquals("Sprockets", attributes.get(RPC_SERVICE));
            assertEquals("GetSprocket", attributes.get(RPC_METHOD));
        }
    }

    DynamicClient createClient(MockQueue queue) throws URISyntaxException {
        var credentials = AwsBasicCredentials.create("access_key", "secret_key");
        return DynamicClient.builder()
                .model(MODEL)
                .serviceId(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .retryStrategy(createRetryStrategy())
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .addPlugin(metricsPlugin)
                .endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost")))
                .putConfig(RegionSetting.REGION, "us-west-2")
                .putSupportedAuthSchemes(new SigV4AuthScheme("sprockets"))
                .authSchemeResolver(AuthSchemeResolver.DEFAULT)
                .addIdentityResolver(new SdkCredentialsResolver(StaticCredentialsProvider.create(credentials)))
                .build();
    }

    static RetryStrategy createRetryStrategy() {
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

    static Map<String, MetricExpectation> mapOfExpectedMetrics() {
        return Map.ofEntries(
                entry(OperationMetrics.DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.ATTEMPTS, new MetricExpectation(MetricDataType.LONG_SUM, "{attempt}")),
                entry(OperationMetrics.ATTEMPT_DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.SERIALIZATION_DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.AUTH_RESOLVE_IDENTITY_DURATION,
                        new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.DESERIALIZATION_DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.RESOLVE_ENDPOINT_DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.REQUEST_PAYLOAD_SIZE, new MetricExpectation(MetricDataType.HISTOGRAM, "bytes")),
                entry(OperationMetrics.RESPONSE_PAYLOAD_SIZE, new MetricExpectation(MetricDataType.HISTOGRAM, "bytes")),
                entry(OperationMetrics.AUTH_SIGNING_DURATION, new MetricExpectation(MetricDataType.HISTOGRAM, "s")),
                entry(OperationMetrics.ERRORS, new MetricExpectation(MetricDataType.LONG_SUM, "{error}")));
    }

    static class MetricExpectation {
        private final MetricDataType dataType;
        private final String unit;

        public MetricExpectation(MetricDataType dataType, String units) {
            this.dataType = dataType;
            this.unit = units;
        }
    }

    private static final class Token implements RetryToken {}
}
