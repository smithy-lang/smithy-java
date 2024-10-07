/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.client.restxml;

import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.client.http.AmznErrorHeaderExtractor;
import software.amazon.smithy.java.runtime.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.runtime.client.http.binding.HttpBindingClientProtocol;
import software.amazon.smithy.java.runtime.client.http.binding.HttpBindingErrorFactory;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.runtime.events.aws.AwsEventDecoderFactory;
import software.amazon.smithy.java.runtime.events.aws.AwsEventEncoderFactory;
import software.amazon.smithy.java.runtime.events.aws.AwsEventFrame;
import software.amazon.smithy.java.runtime.xml.XmlCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Implements aws.protocols#restXml.
 */
public final class RestXmlClientProtocol extends HttpBindingClientProtocol<AwsEventFrame> {

    private final Codec codec;
    private final HttpErrorDeserializer errorDeserializer;

    /**
     * @param service The service being called. This is used when finding the discriminator of documents that use
     *                relative shape IDs.
     */
    public RestXmlClientProtocol(ShapeId service) {
        super(RestXmlTrait.ID.toString());

        this.codec = XmlCodec.builder().build();
        this.errorDeserializer = HttpErrorDeserializer.builder()
            .codec(codec)
            .serviceId(service)
            .knownErrorFactory(new HttpBindingErrorFactory())
            .headerErrorExtractor(new AmznErrorHeaderExtractor())
            .build();
    }

    @Override
    protected Codec codec() {
        return codec;
    }

    @Override
    protected boolean omitEmptyPayload() {
        return true;
    }

    @Override
    protected String payloadMediaType() {
        return "application/xml";
    }

    @Override
    protected HttpErrorDeserializer getErrorDeserializer(Context context) {
        return errorDeserializer;
    }

    @Override
    protected EventEncoderFactory<AwsEventFrame> getEventEncoderFactory(
        InputEventStreamingApiOperation<?, ?, ?> inputOperation
    ) {
        // TODO: this is where you'd plumb through Sigv4 support, another frame transformer?
        return AwsEventEncoderFactory.forInputStream(
            inputOperation,
            codec(),
            payloadMediaType(),
            (e) -> new EventStreamingException("InternalServerException", "Internal Server Error")
        );
    }

    @Override
    protected EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(
        OutputEventStreamingApiOperation<?, ?, ?> outputOperation
    ) {
        return AwsEventDecoderFactory.forOutputStream(outputOperation, codec(), f -> f);
    }

    public static final class Factory implements ClientProtocolFactory<RestXmlTrait> {
        @Override
        public ShapeId id() {
            return RestXmlTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, RestXmlTrait trait) {
            return new RestXmlClientProtocol(settings.service());
        }
    }
}
