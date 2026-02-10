/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.metrics.otel;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * Container for common operation/call metrics.
 */
final class OperationMetrics {
    static final String DURATION = "smithy.client.call.duration";
    static final String ATTEMPTS = "smithy.client.call.attempts";
    static final String ERRORS = "smithy.client.call.errors";
    static final String ATTEMPT_DURATION = "smithy.client.call.attempt_duration";
    static final String SERIALIZATION_DURATION = "smithy.client.call.serialization_duration";
    static final String DESERIALIZATION_DURATION = "smithy.client.call.deserialization_duration";
    static final String RESOLVE_ENDPOINT_DURATION = "smithy.client.call.resolve_endpoint_duration";
    static final String REQUEST_PAYLOAD_SIZE = "smithy.client.call.request_payload_size";
    static final String RESPONSE_PAYLOAD_SIZE = "smithy.client.call.response_payload_size";
    static final String AUTH_RESOLVE_IDENTITY_DURATION = "smithy.client.call.auth.resolve_identity_duration";
    static final String AUTH_SIGNING_DURATION = "smithy.client.call.auth.signing_duration";

    private final DoubleHistogram rpcCallDuration;
    private final LongCounter rpcAttempts;
    private final LongCounter rpcErrors;
    private final DoubleHistogram rpcAttemptDuration;
    private final DoubleHistogram serializationDuration;
    private final DoubleHistogram deserializationDuration;
    private final DoubleHistogram resolveEndpointDuration;
    private final DoubleHistogram resolveIdentityDuration;
    private final DoubleHistogram signingDuration;
    private final DoubleHistogram requestPayloadSize;
    private final DoubleHistogram responsePayloadSize;

    /**
     * Creates a new operation metrics instance.
     *
     * @param meter the instruments provider used to record metrics
     */
    OperationMetrics(Meter meter) {
        this.rpcCallDuration = meter.histogramBuilder(DURATION)
                .setUnit("s")
                .setDescription("Overall call duration including retries")
                .build();
        this.rpcAttempts = meter.counterBuilder(ATTEMPTS)
                .setUnit("{attempt}")
                .setDescription("The number of attempts for an operation")
                .build();
        this.rpcErrors = meter.counterBuilder(ERRORS)
                .setUnit("{error}")
                .setDescription("The number of errors for an operation")
                .build();
        this.rpcAttemptDuration = meter.histogramBuilder(ATTEMPT_DURATION)
                .setUnit("s")
                .setDescription(
                        "The time it takes to connect to complete an entire call attempt, including identity resolution, "
                                +
                                "endpoint resolution, signing, sending the request, and receiving the HTTP status code "
                                +
                                "and headers from the response for an operation")
                .build();
        this.serializationDuration = meter.histogramBuilder(SERIALIZATION_DURATION)
                .setUnit("s")
                .setDescription("The time it takes to serialize a requestg")
                .build();
        this.deserializationDuration = meter.histogramBuilder(DESERIALIZATION_DURATION)
                .setUnit("s")
                .setDescription("The time it takes to deserialize a response")
                .build();
        this.resolveEndpointDuration = meter.histogramBuilder(RESOLVE_ENDPOINT_DURATION)
                .setUnit("s")
                .setDescription("The time it takes to resolve an endpoint for a request")
                .build();
        this.resolveIdentityDuration = meter.histogramBuilder(AUTH_RESOLVE_IDENTITY_DURATION)
                .setUnit("s")
                .setDescription("The time it takes to resolve an identity for signing a request")
                .build();
        this.signingDuration = meter.histogramBuilder(AUTH_SIGNING_DURATION)
                .setUnit("s")
                .setDescription("The time it takes to sign a request")
                .build();
        this.requestPayloadSize = meter.histogramBuilder(REQUEST_PAYLOAD_SIZE)
                .setUnit("bytes")
                .setDescription("The payload size of a request")
                .build();
        this.responsePayloadSize = meter.histogramBuilder(RESPONSE_PAYLOAD_SIZE)
                .setUnit("bytes")
                .setDescription("The payload size of a response")
                .build();
    }

    DoubleHistogram rpcCallDuration() {
        return rpcCallDuration;
    }

    LongCounter rpcAttempts() {
        return rpcAttempts;
    }

    LongCounter rpcErrors() {
        return rpcErrors;
    }

    DoubleHistogram rpcAttemptDuration() {
        return rpcAttemptDuration;
    }

    DoubleHistogram serializationDuration() {
        return serializationDuration;
    }

    DoubleHistogram deserializationDuration() {
        return deserializationDuration;
    }

    DoubleHistogram resolveEndpointDuration() {
        return resolveEndpointDuration;
    }

    DoubleHistogram resolveIdentityDuration() {
        return resolveIdentityDuration;
    }

    DoubleHistogram signingDuration() {
        return signingDuration;
    }

    DoubleHistogram requestPayloadSize() {
        return requestPayloadSize;
    }

    DoubleHistogram responsePayloadSize() {
        return responsePayloadSize;
    }
}
