/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.nio.ByteBuffer;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ErrorFault;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * An opinionated but configurable abstraction for deserializing errors from HTTP responses.
 */
public final class HttpErrorDeserializer {

    /**
     * Extract error shape IDs from HTTP headers and map that ID to a builder using a {@link TypeRegistry}.
     */
    public interface HeaderErrorExtractor {
        /**
         * Check if the response has an error header needed by the extractor.
         *
         * @param response Response to check.
         * @return true if the header is present.
         */
        boolean hasHeader(HttpResponse response);

        /**
         * Extract the header from the response and create an appropriate builder from the type registry.
         *
         * @param response Response to check.
         * @param serviceNamespace Namespace to use if an error is relative.
         * @param registry Registry to check for builders.
         * @return the resolved builder, or null if no builder could be found.
         */
        ShapeId resolveId(HttpResponse response, String serviceNamespace, TypeRegistry registry);
    }

    /**
     * A factory used to create errors that don't match a known error type.
     */
    @FunctionalInterface
    public interface UnknownErrorFactory {
        CallException createError(ErrorFault fault, String message, HttpResponse response);
    }

    /**
     * A factory used to create known errors that are successfully mapped to a builder.
     */
    @FunctionalInterface
    public interface KnownErrorFactory {
        /**
         * Create an error from an HTTP response.
         *
         * @param context Context of the call.
         * @param codec Codec used to deserialize payloads.
         * @param response Response to parse.
         * @param builder Builder to populate and build.
         * @return the created error.
         */
        ModeledException createError(
                Context context,
                Codec codec,
                HttpResponse response,
                ShapeBuilder<ModeledException> builder
        );

        /**
         * Create an error from an HTTP response and a parsed document value.
         *
         * <p>This method can be used when protocols only need to inspect the payload of an error to deserialize it.
         * The default implementation of this method will create a new response based on the provided bytes and
         * delegate to {@link #createError(Context, Codec, HttpResponse, ShapeBuilder)}. Override this method
         * if the protocol can parse errors from documents to avoid parsing the response a second time.
         *
         * @param context Context of the call.
         * @param codec Codec used to deserialize payloads.
         * @param response Response to parse.
         * @param responsePayload The already read bytes of the response payload.
         * @param parsedDocument The already parsed document of the response payload.
         * @param builder Builder to populate and build.
         * @return the created error.
         */
        default ModeledException createErrorFromDocument(
                Context context,
                Codec codec,
                HttpResponse response,
                ByteBuffer responsePayload,
                Document parsedDocument,
                ShapeBuilder<ModeledException> builder
        ) {
            return createError(
                    context,
                    codec,
                    // Make a new response that uses the previously read response payload.
                    response.toBuilder().body(DataStream.ofByteBuffer(responsePayload)).build(),
                    builder);
        }
    }

    /**
     * To create an error, the {@link ShapeId} of the error is required to retrieve the corresponding {@link ShapeBuilder}.
     * Different protocols need different parsers to extract the ShapeId given their different response structures.
     * If no parser specified, {@link #DEFAULT_ERROR_PAYLOAD_PARSER} will be picked.
     */
    @FunctionalInterface
    public interface ErrorPayloadParser {
        /**
         * This method should parse the response payload and extract error's ShapeId,and
         * create the corresponding error with the {@link KnownErrorFactory}.
         *
         * @param context Context of the call.
         * @param codec Codec used to deserialize payloads.
         * @param knownErrorFactory The knownErrorFactory to create error.
         * @param serviceId The ShapeId of the service.
         * @param typeRegistry The error typeRegistry to retrieve builder for the error.
         * @param response Response to parse.
         * @param buffer Bytebuffer of the payload.
         *
         * @return the created error.
         */
        CallException parsePayload(
                Context context,
                Codec codec,
                KnownErrorFactory knownErrorFactory,
                ShapeId serviceId,
                TypeRegistry typeRegistry,
                HttpResponse response,
                ByteBuffer buffer
        ) throws SerializationException, DiscriminatorException;
    }

    // Does not check for any error headers by default.
    private static final HeaderErrorExtractor DEFAULT_EXTRACTOR = new HeaderErrorExtractor() {
        @Override
        public boolean hasHeader(HttpResponse response) {
            return false;
        }

        @Override
        public ShapeId resolveId(HttpResponse response, String serviceNamespace, TypeRegistry registry) {
            return null;
        }
    };

    // Throws an ApiException.
    private static final UnknownErrorFactory DEFAULT_UNKNOWN_FACTORY =
            (fault, message, response) -> new CallException(message, fault);

    // Deserializes without HTTP bindings.
    private static final KnownErrorFactory DEFAULT_KNOWN_FACTORY = new KnownErrorFactory() {
        @Override
        public ModeledException createError(
                Context context,
                Codec codec,
                HttpResponse response,
                ShapeBuilder<ModeledException> builder
        ) {
            try {
                ByteBuffer bytes = createDataStream(response).asByteBuffer();
                return codec.deserializeShape(bytes, builder);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize error", e);
            }
        }

        @Override
        public ModeledException createErrorFromDocument(
                Context context,
                Codec codec,
                HttpResponse response,
                ByteBuffer responsePayload,
                Document parsedDocument,
                ShapeBuilder<ModeledException> builder
        ) {
            parsedDocument.deserializeInto(builder);
            return builder.errorCorrection().build();
        }
    };

    // This default parser should work for most protocols, but other  protocols
    // that do not support document types will need a custom parser to extract error ShapeId.
    private static final ErrorPayloadParser DEFAULT_ERROR_PAYLOAD_PARSER = (
            Context context,
            Codec codec,
            KnownErrorFactory knownErrorFactory,
            ShapeId serviceId,
            TypeRegistry typeRegistry,
            HttpResponse response,
            ByteBuffer buffer) -> {
        var document = codec.createDeserializer(buffer).readDocument();
        var id = document.parseErrorType();
        var builder = typeRegistry.createBuilder(id, ModeledException.class);
        if (builder != null) {
            return knownErrorFactory.createErrorFromDocument(
                    context,
                    codec,
                    response,
                    buffer,
                    document,
                    builder);
        }
        return null;
    };

    private final Codec codec;
    private final HeaderErrorExtractor headerErrorExtractor;
    private final ShapeId serviceId;
    private final UnknownErrorFactory unknownErrorFactory;
    private final KnownErrorFactory knownErrorFactory;
    private final ErrorPayloadParser errorPayloadParser;

    private HttpErrorDeserializer(
            Codec codec,
            HeaderErrorExtractor headerErrorExtractor,
            ShapeId serviceId,
            UnknownErrorFactory unknownErrorFactory,
            KnownErrorFactory knownErrorFactory,
            ErrorPayloadParser errorPayloadParser
    ) {
        this.codec = Objects.requireNonNull(codec, "Missing codec");
        this.serviceId = Objects.requireNonNull(serviceId, "Missing serviceId");
        this.headerErrorExtractor = headerErrorExtractor;
        this.unknownErrorFactory = unknownErrorFactory;
        this.knownErrorFactory = knownErrorFactory;
        this.errorPayloadParser = errorPayloadParser;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CallException createError(
            Context context,
            ShapeId operation,
            TypeRegistry typeRegistry,
            HttpResponse response
    ) {
        var hasErrorHeader = headerErrorExtractor.hasHeader(response);

        if (hasErrorHeader) {
            return makeErrorFromHeader(context, operation, typeRegistry, response);
        }

        var content = createDataStream(response);
        if (content.contentLength() == 0) {
            // No error header, no __type: it's an unknown error.
            return createErrorFromHints(operation, response, unknownErrorFactory);
        } else {
            return makeErrorFromPayload(
                    context,
                    codec,
                    knownErrorFactory,
                    unknownErrorFactory,
                    errorPayloadParser,
                    operation,
                    serviceId,
                    typeRegistry,
                    response,
                    content);
        }
    }

    private static DataStream createDataStream(HttpResponse response) {
        return DataStream.ofPublisher(response.body(), response.contentType(), response.contentLength(-1));
    }

    private CallException makeErrorFromHeader(
            Context context,
            ShapeId operation,
            TypeRegistry typeRegistry,
            HttpResponse response
    ) {
        // The content can be parsed directly here rather than through an intermediate document like with __type.
        var id = headerErrorExtractor.resolveId(response, serviceId.getNamespace(), typeRegistry);
        var builder = id == null ? null : typeRegistry.createBuilder(id, ModeledException.class);

        if (builder == null) {
            // The header didn't match a known error, so create an error from protocol hints.
            return createErrorFromHints(operation, response, unknownErrorFactory);
        } else {
            return knownErrorFactory.createError(context, codec, response, builder);
        }
    }

    private static CallException makeErrorFromPayload(
            Context context,
            Codec codec,
            KnownErrorFactory knownErrorFactory,
            UnknownErrorFactory unknownErrorFactory,
            ErrorPayloadParser errorPayloadParser,
            ShapeId operationId,
            ShapeId serviceId,
            TypeRegistry typeRegistry,
            HttpResponse response,
            DataStream content
    ) {
        try {
            // Read the payload into a JSON document so we can efficiently find __type and then directly
            // deserialize the document into the identified builder.
            ByteBuffer buffer = content.asByteBuffer();

            if (buffer.remaining() > 0) {
                var error = errorPayloadParser.parsePayload(
                        context,
                        codec,
                        knownErrorFactory,
                        serviceId,
                        typeRegistry,
                        response,
                        buffer);
                if (error != null) {
                    return error;
                }
            }
        } catch (SerializationException | DiscriminatorException ignored) {
            // Ignore parsing errors here if the service is returning garbage
        }

        return createErrorFromHints(operationId, response, unknownErrorFactory);
    }

    private static CallException createErrorFromHints(
            ShapeId operationId,
            HttpResponse response,
            UnknownErrorFactory unknownErrorFactory
    ) {
        ErrorFault fault = ErrorFault.ofHttpStatusCode(response.statusCode());
        String message = switch (fault) {
            case CLIENT -> "Client ";
            case SERVER -> "Server ";
            default -> "Unknown ";
        } + response.httpVersion() + ' ' + response.statusCode() + " response from operation " + operationId + ".";
        return unknownErrorFactory.createError(fault, message, response);
    }

    public static final class Builder {
        private Codec codec;
        private HeaderErrorExtractor headerErrorExtractor = DEFAULT_EXTRACTOR;
        private ShapeId serviceId;
        private UnknownErrorFactory unknownErrorFactory = DEFAULT_UNKNOWN_FACTORY;
        private KnownErrorFactory knownErrorFactory = DEFAULT_KNOWN_FACTORY;
        private ErrorPayloadParser errorPayloadParser = DEFAULT_ERROR_PAYLOAD_PARSER;

        private Builder() {}

        public HttpErrorDeserializer build() {
            return new HttpErrorDeserializer(
                    codec,
                    headerErrorExtractor,
                    serviceId,
                    unknownErrorFactory,
                    knownErrorFactory,
                    errorPayloadParser);
        }

        /**
         * The required codec used to deserialize response payloads.
         *
         * @param codec Codec to use.
         * @return the builder.
         */
        public Builder codec(Codec codec) {
            this.codec = Objects.requireNonNull(codec, "codec is null");
            return this;
        }

        /**
         * The shape ID of the service that was called (used for things like resolving relative error shape IDs).
         *
         * @param serviceId Service shape ID.
         * @return the builder.
         */
        public Builder serviceId(ShapeId serviceId) {
            this.serviceId = Objects.requireNonNull(serviceId, "serviceId is null");
            return this;
        }

        /**
         * Configures how headers can be used to extract the shape ID of an error.
         *
         * @param headerErrorExtractor An abstraction used to determine if an error shape ID is present as a header.
         * @return the builder.
         */
        public Builder headerErrorExtractor(HeaderErrorExtractor headerErrorExtractor) {
            this.headerErrorExtractor = Objects.requireNonNull(headerErrorExtractor, "headerErrorExtractor is null");
            return this;
        }

        /**
         * Creates errors from an HTTP response for known errors.
         *
         * <p>A default implementation is used that attempts to simply deserialize the response using only the payload
         * of the response. If HTTP bindings are necessary, pass a custom factory.
         *
         * @param knownErrorFactory Factory used to create error shapes for known errors.
         * @return the builder.
         */
        public Builder knownErrorFactory(KnownErrorFactory knownErrorFactory) {
            this.knownErrorFactory = Objects.requireNonNull(knownErrorFactory, "knownErrorFactory is null");
            return this;
        }

        /**
         * The factory to use to create unknown errors.
         *
         * <p>By default, an {@link CallException} is created for generic unknown errors.
         *
         * @param unknownErrorFactory Factory used to create generic errors that don't match a known error type.
         * @return the builder.
         */
        public Builder unknownErrorFactory(UnknownErrorFactory unknownErrorFactory) {
            this.unknownErrorFactory = Objects.requireNonNull(unknownErrorFactory, "unknownErrorFactory is null");
            return this;
        }

        /**
         * The parser to parse the shapeId from the payload.
         *
         * <p>The default parser implementation will parse the payload into a {@link Document} and
         * use {@link Document#discriminator()} to extract its {@code __type} field as ShapeId
         *
         * @param errorPayloadParser Parser used to parse the payload.
         * @return the builder.
         */
        public Builder errorPayloadParser(ErrorPayloadParser errorPayloadParser) {
            this.errorPayloadParser = Objects.requireNonNull(errorPayloadParser, "ErrorPayloadParser is null");
            return this;
        }
    }
}
