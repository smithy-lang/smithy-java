/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.restxml;

import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.java.aws.events.AwsEventDecoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventEncoderFactory;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.http.AmznErrorHeaderExtractor;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.client.http.binding.HttpBindingClientProtocol;
import software.amazon.smithy.java.client.http.binding.HttpBindingErrorFactory;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.xml.XmlCodec;
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
        super(RestXmlTrait.ID);

        this.codec = XmlCodec.builder().build();
        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(codec)
                .serviceId(service)
                .knownErrorFactory(new HttpBindingErrorFactory(httpBinding()))
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .build();
    }

    @Override
    public Codec payloadCodec() {
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
                payloadCodec(),
                payloadMediaType(),
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    @Override
    protected EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(
            OutputEventStreamingApiOperation<?, ?, ?> outputOperation
    ) {
        return AwsEventDecoderFactory.forOutputStream(outputOperation, payloadCodec(), f -> f);
    }

    @Override
    protected <I extends SerializableStruct, O extends SerializableStruct> CallException createError(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
    ) {
        return errorDeserializer.createErrorForXml(context, operation.schema().id(), typeRegistry, response);
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
