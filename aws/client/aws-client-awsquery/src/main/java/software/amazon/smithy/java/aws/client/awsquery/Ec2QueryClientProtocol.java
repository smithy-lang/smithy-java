/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.http.HttpClientProtocol;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.xml.XmlCodec;
import software.amazon.smithy.java.xml.XmlUtil;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Implements the {@code aws.protocols#ec2Query} protocol.
 */
public final class Ec2QueryClientProtocol extends HttpClientProtocol {

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final String version;
    private final HttpErrorDeserializer errorDeserializer;
    private final XmlCodec codec = XmlCodec.builder().build();

    public Ec2QueryClientProtocol(ShapeId service, String version) {
        super(Ec2QueryTrait.ID);
        Objects.requireNonNull(service, "service is required");
        this.version = Objects.requireNonNull(version, "version is required");
        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(codec)
                .serviceId(service)
                .errorPayloadParser(new Ec2ErrorPayloadParser())
                .knownErrorFactory(new XmlKnownErrorFactory())
                .build();
    }

    @Override
    public Codec payloadCodec() {
        return codec;
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            SmithyUri endpoint
    ) {
        String operationName = operation.schema().id().getName();
        QueryFormSerializer serializer = new QueryFormSerializer(
                QueryFormSerializer.QueryVariant.EC2_QUERY,
                operationName,
                version);

        if (!operation.inputSchema().hasTrait(TraitKey.UNIT_TYPE_TRAIT)) {
            input.serializeMembers(serializer);
        }

        ByteBuffer body = serializer.finish();

        return HttpRequest.create()
                .setMethod("POST")
                .setUri(endpoint)
                .setHeader(HeaderName.CONTENT_TYPE, CONTENT_TYPE)
                .setBody(DataStream.ofByteBuffer(body, CONTENT_TYPE));
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> O deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
    ) {
        if (response.statusCode() >= 300) {
            throw errorDeserializer.createError(context, operation, typeRegistry, response);
        }

        var builder = operation.outputBuilder();
        var content = response.body();

        if (content.contentLength() == 0) {
            return builder.build();
        }

        // EC2 Query response wraps output in OperationResponse only (no OperationResult)
        var operationName = operation.schema().id().getName();
        try (var codec = XmlCodec.builder()
                .wrapperElements(List.of(operationName + "Response"))
                .build()) {
            return codec.deserializeShape(response.body().asByteBuffer(), builder);
        }
    }

    /**
     * EC2 Query error format: {@code Response > Errors > Error > Code}.
     * No awsQueryError trait — error code is just the shape name.
     */
    private static final class Ec2ErrorPayloadParser implements HttpErrorDeserializer.ErrorPayloadParser {
        @Override
        public CallException parsePayload(
                Context context,
                Codec codec,
                HttpErrorDeserializer.KnownErrorFactory knownErrorFactory,
                ShapeId serviceId,
                TypeRegistry typeRegistry,
                ApiOperation<?, ?> operation,
                HttpResponse response,
                ByteBuffer buffer
        ) {
            var deserializer = codec.createDeserializer(buffer);
            String code = XmlUtil.parseErrorCodeName(deserializer);

            var id = ShapeId.fromOptionalNamespace(serviceId.getNamespace(), code);
            ShapeBuilder<ModeledException> builder = typeRegistry.createBuilder(id, ModeledException.class);

            if (builder != null) {
                return knownErrorFactory.createError(context, codec, response, builder);
            }
            return null;
        }

        @Override
        public ShapeId extractErrorType(Document document, String namespace) {
            return null;
        }
    }

    public static final class Factory implements ClientProtocolFactory<Ec2QueryTrait> {

        @Override
        public ShapeId id() {
            return Ec2QueryTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, Ec2QueryTrait trait) {
            return new Ec2QueryClientProtocol(
                    Objects.requireNonNull(settings.service(), "service is a required protocol setting"),
                    Objects.requireNonNull(settings.serviceVersion(),
                            "serviceVersion is a required protocol setting for EC2 Query."));
        }
    }
}
