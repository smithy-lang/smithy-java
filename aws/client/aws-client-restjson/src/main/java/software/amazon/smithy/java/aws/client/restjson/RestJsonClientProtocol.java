/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.restjson;

import java.net.URI;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
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
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.core.serde.event.FrameTransformer;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.binding.RequestSerializer;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Implements aws.protocols#restJson1.
 */
public final class RestJsonClientProtocol extends HttpBindingClientProtocol<AwsEventFrame> {

    private final JsonCodec codec;
    private final HttpErrorDeserializer errorDeserializer;

    /**
     * @param service The service being called. This is used when finding the discriminator of documents that use
     *                relative shape IDs.
     */
    public RestJsonClientProtocol(ShapeId service) {
        super(RestJson1Trait.ID);

        this.codec = JsonCodec.builder()
                .useJsonName(true)
                .useTimestampFormat(true)
                .defaultNamespace(service.getNamespace())
                .build();

        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(codec)
                .serviceId(service)
                .knownErrorFactory(new HttpBindingErrorFactory(httpBinding()))
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .build();
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
    ) {
        RequestSerializer serializer = httpBinding().requestSerializer()
                .operation(operation)
                .payloadCodec(payloadCodec())
                .payloadMediaType(payloadMediaType())
                .shapeValue(input)
                .endpoint(endpoint)
                .omitEmptyPayload(omitEmptyPayload())
                .allowEmptyStructPayload(hasStructPayload(input));

        if (operation instanceof InputEventStreamingApiOperation<?, ?, ?> i) {
            serializer.eventEncoderFactory(getEventEncoderFactory(i));
        }

        return serializer.serializeRequest();
    }

    @Override
    public Codec payloadCodec() {
        return codec;
    }

    @Override
    protected String payloadMediaType() {
        return "application/json";
    }

    @Override
    protected HttpErrorDeserializer getErrorDeserializer(Context context) {
        return errorDeserializer;
    }

    @Override
    protected boolean omitEmptyPayload() {
        return true;
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
                FrameTransformer.identity(),
                (e) -> new EventStreamingException("InternalServerException", "Internal Server Error"));
    }

    @Override
    protected EventDecoderFactory<AwsEventFrame> getEventDecoderFactory(
            OutputEventStreamingApiOperation<?, ?, ?> outputOperation
    ) {
        return AwsEventDecoderFactory.forOutputStream(outputOperation, payloadCodec(), f -> f);
    }

    private <I extends SerializableStruct> boolean hasStructPayload(I input) {
        var members = input.schema().members();
        for (var member : members) {
            if (member.type().equals(ShapeType.STRUCTURE)
                    && member.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT)) {
                return true;
            }
        }
        return false;
    }

    public static final class Factory implements ClientProtocolFactory<RestJson1Trait> {
        @Override
        public ShapeId id() {
            return RestJson1Trait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, RestJson1Trait trait) {
            return new RestJsonClientProtocol(settings.service());
        }
    }
}
