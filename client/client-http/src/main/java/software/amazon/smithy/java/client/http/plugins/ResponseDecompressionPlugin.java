/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.util.Arrays;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.compression.DecompressionAlgorithm;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Asks for compressed responses and decompresses them with the supported {@link DecompressionAlgorithm}s.
 *
 * <p>Add it to a client with {@code Client.Builder#addPlugin}. Requests that already set {@code Accept-Encoding}
 * and operations with a streaming output are left as they are.
 */
public final class ResponseDecompressionPlugin implements ClientPlugin {

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (config.isUsingMessageExchange(HttpMessageExchange.INSTANCE)) {
            config.addInterceptor(ResponseDecompressionInterceptor.INSTANCE);
        }
    }

    static final class ResponseDecompressionInterceptor implements ClientInterceptor {

        private static final ClientInterceptor INSTANCE = new ResponseDecompressionInterceptor();
        private static final Context.Key<Boolean> DECOMPRESS_RESPONSE =
                Context.key("HTTP.DecompressResponse");
        private static final String ACCEPT_ENCODING = String.join(", ",
                DecompressionAlgorithm.supportedAlgorithms()
                        .stream()
                        .map(DecompressionAlgorithm::algorithmId)
                        .toList());
        private static final String IDENTITY = "identity";

        @Override
        public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
            if (hook.request() instanceof HttpRequest req
                    && hook.operation().outputStreamMember() == null
                    && !req.headers().hasHeader(HeaderName.ACCEPT_ENCODING)) {
                hook.context().put(DECOMPRESS_RESPONSE, true);
                var acceptingRequest = req.toModifiableCopy()
                        .setHeader(HeaderName.ACCEPT_ENCODING, ACCEPT_ENCODING)
                        .toUnmodifiable();
                return hook.asRequestType(acceptingRequest);
            }
            return hook.request();
        }

        @Override
        public <ResponseT> ResponseT modifyBeforeDeserialization(ResponseHook<?, ?, ?, ResponseT> hook) {
            if (!hook.context().getOrDefault(DECOMPRESS_RESPONSE, false)
                    || !(hook.response() instanceof HttpResponse res)) {
                return hook.response();
            }
            return hook.asResponseType(decompress(res));
        }

        /**
         * Decompresses a response whose {@code Content-Encoding} codings are all supported algorithms, undoing them
         * in reverse of the order they were applied. Any other response is returned unchanged. The decoded length is
         * unknown until the body has been read.
         */
        private static HttpResponse decompress(HttpResponse response) {
            var codings = response.headers()
                    .allValues(HeaderName.CONTENT_ENCODING)
                    .stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .filter(coding -> !coding.isEmpty() && !coding.equalsIgnoreCase(IDENTITY))
                    .toList();
            if (codings.isEmpty()) {
                return response;
            }
            var body = response.body();
            for (var coding : codings.reversed()) {
                var algorithm = supportedAlgorithm(coding);
                if (algorithm == null) {
                    return response;
                }
                body = algorithm.decompress(body);
            }
            return response.toModifiableCopy()
                    .removeHeader(HeaderName.CONTENT_ENCODING)
                    .removeHeader(HeaderName.CONTENT_LENGTH)
                    .setBody(body)
                    .toUnmodifiable();
        }

        private static DecompressionAlgorithm supportedAlgorithm(String coding) {
            for (var algorithm : DecompressionAlgorithm.supportedAlgorithms()) {
                if (algorithm.algorithmId().equalsIgnoreCase(coding)) {
                    return algorithm;
                }
            }
            return null;
        }
    }
}
