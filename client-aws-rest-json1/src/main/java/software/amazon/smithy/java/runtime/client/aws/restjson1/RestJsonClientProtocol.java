/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.aws.restjson1;

import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.client.http.HttpBindingClientProtocol;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.runtime.events.aws.AwsEventDecoderFactory;
import software.amazon.smithy.java.runtime.events.aws.AwsEventEncoderFactory;
import software.amazon.smithy.java.runtime.events.aws.AwsEventFrame;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Implements aws.protocols#restJson1.
 */
public final class RestJsonClientProtocol extends HttpBindingClientProtocol<AwsEventFrame> {
    public static final ShapeId ID = ShapeId.from("aws.protocols#restJson1");

    public RestJsonClientProtocol() {
        this(null);
    }

    /**
     * @param serviceNamespace The namespace of the service being called. This is used when finding the
     *                         discriminator of documents that use relative shape IDs.
     */
    public RestJsonClientProtocol(String serviceNamespace) {
        super(
            ID.toString(),
            JsonCodec.builder()
                .useJsonName(true)
                .useTimestampFormat(true)
                .defaultNamespace(serviceNamespace)
                .build()
        );
    }

    @Override
    protected EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(
        InputEventStreamingApiOperation<?, ?, ?> inputOperation
    ) {
        // TODO: this is where you'd plumb through Sigv4 support, another frame transformer?
        return AwsEventEncoderFactory.forInputStream(
            inputOperation,
            codec,
            (e) -> new EventStreamingException("InternalServerException", "Internal Server Error")
        );
    }

    @Override
    protected EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(
        OutputEventStreamingApiOperation<?, ?, ?> outputOperation
    ) {
        return AwsEventDecoderFactory.forOutputStream(
            outputOperation,
            codec,
            f -> f
        );
    }

    public static final class Factory implements ClientProtocolFactory {
        @Override
        public ShapeId id() {
            return ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings) {
            return new RestJsonClientProtocol(settings.namespace());
        }
    }
}
