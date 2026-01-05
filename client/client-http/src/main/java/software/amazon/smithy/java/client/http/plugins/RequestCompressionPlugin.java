/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.util.List;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.compression.CompressionAlgorithm;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.traits.RequestCompressionTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Compress the request body using provided compression algorithm if @requestCompression trait is applied.
 */
@SmithyInternalApi
public final class RequestCompressionPlugin implements AutoClientPlugin {

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (config.isUsingMessageExchange(HttpMessageExchange.INSTANCE)) {
            config.addInterceptor(RequestCompressionInterceptor.INSTANCE);
        }
    }

    static final class RequestCompressionInterceptor implements ClientInterceptor {

        private static final int DEFAULT_MIN_COMPRESSION_SIZE_BYTES = 10240;
        // This cap matches ApiGateway's spec: https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-openapi-minimum-compression-size.html
        private static final int MIN_COMPRESSION_SIZE_CAP = 10485760;
        private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
        private static final ClientInterceptor INSTANCE = new RequestCompressionInterceptor();
        private static final TraitKey<RequestCompressionTrait> REQUEST_COMPRESSION_TRAIT_KEY =
                TraitKey.get(RequestCompressionTrait.class);
        // Currently only Gzip is supported in Smithy model: https://smithy.io/2.0/spec/behavior-traits.html#requestcompression-trait
        private static final List<CompressionAlgorithm> supportedAlgorithms =
                CompressionAlgorithm.supportedAlgorithms();

        @Override
        public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
            return hook.mapRequest(HttpRequest.class, RequestCompressionInterceptor::processRequest);
        }

        private static HttpRequest processRequest(RequestHook<?, ?, HttpRequest> hook) {
            if (shouldCompress(hook)) {
                var compressionTrait =
                        hook.operation().schema().getTrait(REQUEST_COMPRESSION_TRAIT_KEY);
                var request = hook.request();
                // Will pick the first supported algorithm to compress the body.
                for (var algorithmId : compressionTrait.getEncodings()) {
                    for (var algorithm : supportedAlgorithms) {
                        if (algorithmId.equals(algorithm.algorithmId())) {
                            var compressed = algorithm.compress(request.body());
                            return request.toBuilder()
                                    .body(compressed)
                                    .withAddedHeader(CONTENT_ENCODING_HEADER, algorithmId)
                                    .build();
                        }
                    }
                }
            }
            return hook.request();
        }

        private static boolean shouldCompress(RequestHook<?, ?, HttpRequest> hook) {
            var context = hook.context();
            var operation = hook.operation();
            if (!operation.schema().hasTrait(REQUEST_COMPRESSION_TRAIT_KEY)
                    || context.getOrDefault(HttpContext.DISABLE_REQUEST_COMPRESSION, false)) {
                return false;
            }
            var requestBody = hook.request().body();
            // Streaming should not have known length
            if (operation.inputStreamMember() != null && !requestBody.hasKnownLength()) {
                return true;
            }
            return isBodySizeValid(requestBody, context);
        }

        private static boolean isBodySizeValid(DataStream requestBody, Context context) {
            var minCompressionSize = context.getOrDefault(HttpContext.REQUEST_MIN_COMPRESSION_SIZE_BYTES,
                    DEFAULT_MIN_COMPRESSION_SIZE_BYTES);
            validateCompressionSize(minCompressionSize);
            return requestBody.contentLength() >= minCompressionSize;
        }

        private static void validateCompressionSize(int minCompressionSize) {
            if (minCompressionSize < 0 || minCompressionSize > MIN_COMPRESSION_SIZE_CAP) {
                throw new IllegalArgumentException("Min compression size must be between 0 and 10485760");
            }
        }
    }
}
