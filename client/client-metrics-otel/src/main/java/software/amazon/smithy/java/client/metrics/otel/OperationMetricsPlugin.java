/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.metrics.otel;

import io.opentelemetry.api.OpenTelemetry;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * A plugin that instruments the client to emit request/response metrics using
 * <a href="https://opentelemetry.io/">OpenTelemetry</a>.
 *
 * <h2>Usage example: OTLP → CloudWatch Agent</h2>
 *
 * <p>The following example shows how to configure OpenTelemetry to publish the metrics to
 * <a href="https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OpenTelemetry-Sections.html">
 * AWS CloudWatch using the EC2 CloudWatch client.</a></p>
 *
 * <p>This is the most common pattern. Your Java app sends metrics via OTLP to a locally running CloudWatch agent.</p>
 * <p>
 * {@snippet lang = "java":
 *     public static OperationMetricsPlugin createPlugin() {
 *         // Configure the SDK meter provider to use the local EC2 CloudWatch agent
 *         // Define resource attributes
 *         Resource resource = Resource.getDefault().toBuilder()
 *             .put(ResourceAttributes.SERVICE_NAME, "my-java-service")
 *             .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
 *             .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production")
 *             .build();
 *
 *         // Create OTLP HTTP exporter pointing to CloudWatch agent
 *         OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
 *             .setEndpoint("http://localhost:4318/v1/metrics")  // CloudWatch agent endpoint
 *             .build();
 *
 *         // Or use gRPC (port 4317)
 *         // OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
 *         //     .setEndpoint("http://localhost:4317")
 *         //     .build();
 *
 *         // Create metric reader with export interval
 *         PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
 *             .setInterval(Duration.ofSeconds(60))
 *             .build();
 *
 *         // Create meter provider
 *         SdkMeterProvider meterProvider = SdkMeterProvider.builder()
 *             .setResource(resource)
 *             .registerMetricReader(metricReader)
 *             .build();
 *
 *         // Build and register global OpenTelemetry
 *         OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
 *             .setMeterProvider(meterProvider)
 *             .buildAndRegisterGlobal();
 *
 *         // Create the plugin with the OpenTelemetry instance
 *         return new OperationMetricsPlugin(openTelemetry);
 *     }
 *
 *     public static DynamicClient createClient() {
 *         // Using the plugin with a client.
 *         return DynamicClient.builder()
 *             .addPlugin(createPlugin())
 *             .build();
 *     }
 *}
 *
 * <h2>Metrics published</h2>
 * <p>This plugin publishes the following metrics for each request</p>
 *
 * <dl>
 *   <dt>smithy.client.call.duration (unit: s)</dt>
 *   <dd>
 *     Overall call duration including retries.
 *   </dd>
 *
 *   <dt>smithy.client.call.attempts</dt>
 *   <dd>
 *     The number of attempts for an operation.
 *   </dd>
 *
 *   <dt>smithy.client.call.errors</dt>
 *   <dd>
 *     The number of errors for an operation.
 *   </dd>
 *
 *   <dt>smithy.client.call.attempt_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to connect to complete an entire call attempt, including identity resolution, endpoint resolution, signing, sending the request, and receiving the HTTP status code and headers from the response for an operation.
 *   </dd>
 *
 *   <dt>smithy.client.call.serialization_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to serialize a request message body.
 *   </dd>
 *
 *   <dt>smithy.client.call.deserialization_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to deserialize a response message body.
 *   </dd>
 *
 *   <dt>smithy.client.call.resolve_endpoint_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to resolve an endpoint for a request.
 *   </dd>
 *
 *   <dt>smithy.client.call.auth.resolve_identity_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to resolve an identity for signing a request.
 *   </dd>
 *
 *   <dt>smithy.client.call.auth.signing_duration (unit: s)</dt>
 *   <dd>
 *     The time it takes to sign a request.
 *   </dd>
 *
 *   <dt>smithy.client.call.request_payload_size (unit: bytes)</dt>
 *   <dd>
 *     The payload size of a request.
 *   </dd>
 *
 *   <dt>smithy.client.call.response_payload_size (unit: bytes)</dt>
 *   <dd>
 *     The payload size of a response.
 *   </dd>
 * </dl>
 *
 * <p>The following attributes are attached to each metric</p>
 *
 * <dl>
 *     <dt>rpc.service</dt>
 *     <dd>The name of the service</dd>
 *     <dt>rpc.method</dt>
 *     <dd>The name of the operation being called</dd>
 * </dl>
 *
 * Additionally the following attribute is attached to the smithy.client.call.errors metric
 *
 * <dl>
 *     <dt>excepton.type</dt>
 *     <dt>The name of the class exception</dt>
 * </dl>
 *
 * @see ClientConfig.Builder#addPlugin(ClientPlugin)
 * @see Client.Builder#addPlugin(ClientPlugin)
 */
public final class OperationMetricsPlugin implements ClientPlugin {

    private final OperationMetrics operationMetrics;

    /**
     * Creates a new operation metrics plugin.
     *
     * @param openTelemetry The OpenTelemetry instance used to create metrics
     * @param scope         The scope used to publish metrics.
     */
    public OperationMetricsPlugin(OpenTelemetry openTelemetry, String scope) {
        this.operationMetrics = new OperationMetrics(openTelemetry.getMeter(scope));
    }

    /**
     * Creates a new operation metrics plugin using the default scope <pre>"software.amazon.smithy.java.client"</pre>.
     *
     * @param openTelemetry The OpenTelemetry instance used to create metrics
     */
    public OperationMetricsPlugin(OpenTelemetry openTelemetry) {
        this(openTelemetry, "software.amazon.smithy.java.client");
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(new OperationMetricsInterceptor(operationMetrics));
    }
}
