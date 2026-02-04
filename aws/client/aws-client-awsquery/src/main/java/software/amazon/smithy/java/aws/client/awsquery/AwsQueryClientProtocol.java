/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait;
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
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.xml.XmlCodec;
import software.amazon.smithy.java.xml.XmlUtil;
import software.amazon.smithy.model.shapes.ShapeId;

public final class AwsQueryClientProtocol extends HttpClientProtocol {

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final List<String> CONTENT_TYPE_LIST = List.of(CONTENT_TYPE);
    public static final HttpHeaders CONTENT_TYPE_HEADERS = HttpHeaders.of(Map.of("Content-Type", CONTENT_TYPE_LIST));

    private final ShapeId service;
    private final String version;
    private final HttpErrorDeserializer errorDeserializer;
    private final XmlCodec codec = XmlCodec.builder().build();

    public AwsQueryClientProtocol(ShapeId service, String version) {
        super(AwsQueryTrait.ID);
        this.service = Objects.requireNonNull(service, "service is required");
        this.version = Objects.requireNonNull(version, "version is required");
        this.errorDeserializer = HttpErrorDeserializer.builder()
                .codec(XmlCodec.builder().build())
                .serviceId(service)
                .errorPayloadParser(XML_ERROR_PAYLOAD_PARSER)
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
            URI endpoint
    ) {
        String operationName = operation.schema().id().getName();
        AwsQueryFormSerializer serializer = new AwsQueryFormSerializer(operationName, version);

        if (!Unit.ID.equals(operation.inputSchema().id())) {
            input.serializeMembers(serializer);
        }

        ByteBuffer body = serializer.finish();

        return HttpRequest.builder()
                .method("POST")
                .uri(endpoint)
                .headers(CONTENT_TYPE_HEADERS)
                .body(DataStream.ofByteBuffer(body, CONTENT_TYPE))
                .build();
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
            throw errorDeserializer.createError(context, operation.schema().id(), typeRegistry, response);
        }

        var builder = operation.outputBuilder();
        var content = response.body();

        if (content.contentLength() == 0) {
            return builder.build();
        }

        var operationName = operation.schema().id().getName();
        try (var codec = XmlCodec.builder()
                .wrapperElements(List.of(operationName + "Response", operationName + "Result"))
                .build()) {
            return codec.deserializeShape(response.body().asByteBuffer(), builder);
        }
    }

    private static final HttpErrorDeserializer.ErrorPayloadParser XML_ERROR_PAYLOAD_PARSER =
            new HttpErrorDeserializer.ErrorPayloadParser() {
                @Override
                public CallException parsePayload(
                        Context context,
                        Codec codec,
                        HttpErrorDeserializer.KnownErrorFactory knownErrorFactory,
                        ShapeId serviceId,
                        TypeRegistry typeRegistry,
                        HttpResponse response,
                        ByteBuffer buffer
                ) {
                    var deserializer = codec.createDeserializer(buffer);
                    String code = XmlUtil.parseErrorCodeName(deserializer);
                    var nameSpace = serviceId.getNamespace();
                    var id = ShapeId.fromOptionalNamespace(nameSpace, code);
                    var builder = typeRegistry.createBuilder(id, ModeledException.class);
                    if (builder != null) {
                        return knownErrorFactory.createError(context, codec, response, builder);
                    }
                    return null;
                }

                @Override
                public ShapeId extractErrorType(
                        Document document,
                        String namespace
                ) {
                    return null;
                }
            };

    private static final class XmlKnownErrorFactory implements HttpErrorDeserializer.KnownErrorFactory {
        @Override
        public ModeledException createError(
                Context context,
                Codec codec,
                HttpResponse response,
                ShapeBuilder<ModeledException> builder
        ) {
            ByteBuffer bytes = DataStream.ofPublisher(
                    response.body(),
                    response.contentType(),
                    response.contentLength(-1)).asByteBuffer();
            return codec.deserializeShape(bytes, builder);
        }
    }

    public static final class Factory implements ClientProtocolFactory<AwsQueryTrait> {

        @Override
        public ShapeId id() {
            return AwsQueryTrait.ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, AwsQueryTrait trait) {
            return new AwsQueryClientProtocol(
                    Objects.requireNonNull(settings.service(), "service is a required protocol setting"),
                    Objects.requireNonNull(settings.serviceVersion(),
                            "serviceVersion is a required protocol setting for AWS Query."));
        }
    }
}
