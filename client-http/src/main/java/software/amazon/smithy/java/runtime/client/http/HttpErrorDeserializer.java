/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
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
        boolean hasHeader(SmithyHttpResponse response);

        /**
         * Extract the header from the response and create an appropriate builder from the type registry.
         *
         * @param response Response to check.
         * @param serviceNamespace Namespace to use if an error is relative.
         * @param registry Registry to check for builders.
         * @return the resolved builder, or null if no builder could be found.
         */
        ShapeId resolveId(SmithyHttpResponse response, String serviceNamespace, TypeRegistry registry);
    }

    /**
     * A factory used to create errors that don't match a known error type.
     */
    @FunctionalInterface
    public interface UnknownErrorFactory {
        CompletableFuture<ApiException> createError(
            ApiException.Fault fault,
            String message,
            SmithyHttpResponse response
        );
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
        CompletableFuture<ModeledApiException> createError(
            Context context,
            Codec codec,
            SmithyHttpResponse response,
            ShapeBuilder<ModeledApiException> builder
        );

        /**
         * Create an error from an HTTP response and a parsed document value.
         *
         * <p>This method can be used when protocols only need to inspect the payload of an error to deserialize it.
         * The default implementation of this method will create a new response based on the provided bytes and
         * delegate to {@link #createError(Context, Codec, SmithyHttpResponse, ShapeBuilder)}. Override this method
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
        default CompletableFuture<ModeledApiException> createErrorFromDocument(
            Context context,
            Codec codec,
            SmithyHttpResponse response,
            ByteBuffer responsePayload,
            Document parsedDocument,
            ShapeBuilder<ModeledApiException> builder
        ) {
            return createError(
                context,
                codec,
                // Make a new response that uses the previously read response payload.
                response.toBuilder().body(DataStream.ofByteBuffer(responsePayload)).build(),
                builder
            );
        }
    }

    // Does not check for any error headers by default.
    private static final HeaderErrorExtractor DEFAULT_EXTRACTOR = new HeaderErrorExtractor() {
        @Override
        public boolean hasHeader(SmithyHttpResponse response) {
            return false;
        }

        @Override
        public ShapeId resolveId(SmithyHttpResponse response, String serviceNamespace, TypeRegistry registry) {
            return null;
        }
    };

    // Throws an ApiException.
    private static final UnknownErrorFactory DEFAULT_UNKNOWN_FACTORY = (fault, message, response) -> CompletableFuture
        .completedFuture(new ApiException(message, fault));

    // Deserializes without HTTP bindings.
    private static final KnownErrorFactory DEFAULT_KNOWN_FACTORY = new KnownErrorFactory() {
        @Override
        public CompletableFuture<ModeledApiException> createError(
            Context context,
            Codec codec,
            SmithyHttpResponse response,
            ShapeBuilder<ModeledApiException> builder
        ) {
            return createDataStream(response).asByteBuffer()
                .thenApply(bytes -> codec.deserializeShape(bytes, builder));
        }

        @Override
        public CompletableFuture<ModeledApiException> createErrorFromDocument(
            Context context,
            Codec codec,
            SmithyHttpResponse response,
            ByteBuffer responsePayload,
            Document parsedDocument,
            ShapeBuilder<ModeledApiException> builder
        ) {
            parsedDocument.deserializeInto(builder);
            var result = builder.errorCorrection().build();
            return CompletableFuture.completedFuture(result);
        }
    };

    private final Codec codec;
    private final HeaderErrorExtractor headerErrorExtractor;
    private final ShapeId serviceId;
    private final UnknownErrorFactory unknownErrorFactory;
    private final KnownErrorFactory knownErrorFactory;

    private HttpErrorDeserializer(
        Codec codec,
        HeaderErrorExtractor headerErrorExtractor,
        ShapeId serviceId,
        UnknownErrorFactory unknownErrorFactory,
        KnownErrorFactory knownErrorFactory
    ) {
        this.codec = Objects.requireNonNull(codec, "Missing codec");
        this.serviceId = Objects.requireNonNull(serviceId, "Missing serviceId");
        this.headerErrorExtractor = headerErrorExtractor;
        this.unknownErrorFactory = unknownErrorFactory;
        this.knownErrorFactory = knownErrorFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CompletableFuture<? extends ApiException> createError(
        Context context,
        ShapeId operation,
        TypeRegistry typeRegistry,
        SmithyHttpResponse response
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
            // Look for __type in the payload.
            return makeErrorFromPayload(
                context,
                codec,
                knownErrorFactory,
                unknownErrorFactory,
                operation,
                typeRegistry,
                response,
                content
            );
        }
    }

    private static DataStream createDataStream(SmithyHttpResponse response) {
        return DataStream.ofPublisher(response.body(), response.contentType(), response.contentLength(-1));
    }

    private CompletableFuture<? extends ApiException> makeErrorFromHeader(
        Context context,
        ShapeId operation,
        TypeRegistry typeRegistry,
        SmithyHttpResponse response
    ) {
        // The content can be parsed directly here rather than through an intermediate document like with __type.
        var id = headerErrorExtractor.resolveId(response, serviceId.getNamespace(), typeRegistry);
        var builder = id == null ? null : typeRegistry.createBuilder(id, ModeledApiException.class);

        if (builder == null) {
            // The header didn't match a known error, so create an error from protocol hints.
            return createErrorFromHints(operation, response, unknownErrorFactory);
        } else {
            return knownErrorFactory.createError(context, codec, response, builder);
        }
    }

    private static CompletableFuture<? extends ApiException> makeErrorFromPayload(
        Context context,
        Codec codec,
        KnownErrorFactory knownErrorFactory,
        UnknownErrorFactory unknownErrorFactory,
        ShapeId operationId,
        TypeRegistry typeRegistry,
        SmithyHttpResponse response,
        DataStream content
    ) {
        // Read the payload into a JSON document so we can efficiently find __type and then directly
        // deserialize the document into the identified builder.
        return content.asByteBuffer().thenCompose(buffer -> {
            if (buffer.remaining() > 0) {
                try {
                    var document = codec.createDeserializer(buffer).readDocument();
                    var id = document.discriminator();
                    var builder = typeRegistry.createBuilder(id, ModeledApiException.class);
                    if (builder != null) {
                        return knownErrorFactory
                            .createErrorFromDocument(context, codec, response, buffer, document, builder)
                            .thenApply(e -> e);
                    }
                    // ignore parsing errors here if the service is returning garbage.
                } catch (SerializationException | DiscriminatorException ignored) {}
            }

            return createErrorFromHints(operationId, response, unknownErrorFactory);
        });
    }

    private static CompletableFuture<ApiException> createErrorFromHints(
        ShapeId operationId,
        SmithyHttpResponse response,
        UnknownErrorFactory unknownErrorFactory
    ) {
        ApiException.Fault fault = ApiException.Fault.ofHttpStatusCode(response.statusCode());
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

        private Builder() {}

        public HttpErrorDeserializer build() {
            return new HttpErrorDeserializer(
                codec,
                headerErrorExtractor,
                serviceId,
                unknownErrorFactory,
                knownErrorFactory
            );
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
         * <p>By default, an {@link ApiException} is created for generic unknown errors.
         *
         * @param unknownErrorFactory Factory used to create generic errors that don't match a known error type.
         * @return the builder.
         */
        public Builder unknownErrorFactory(UnknownErrorFactory unknownErrorFactory) {
            this.unknownErrorFactory = Objects.requireNonNull(unknownErrorFactory, "unknownErrorFactory is null");
            return this;
        }
    }
}
