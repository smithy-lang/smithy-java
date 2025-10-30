/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.HttpChecksumRequiredTrait;
import software.amazon.smithy.utils.ListUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Plugin that adds Content-MD5 header for operations with @httpChecksumRequired trait.
 */
@SmithyInternalApi
public final class HttpChecksumPlugin implements ClientPlugin {

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(HttpChecksumInterceptor.INSTANCE);
    }

    static final class HttpChecksumInterceptor implements ClientInterceptor {
        private static final ClientInterceptor INSTANCE = new HttpChecksumInterceptor();
        private static final TraitKey<HttpChecksumRequiredTrait> CHECKSUM_REQUIRED_TRAIT_KEY =
                TraitKey.get(HttpChecksumRequiredTrait.class);

        @Override
        public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
            return hook.mapRequest(HttpRequest.class, HttpChecksumInterceptor::processRequest);
        }

        private static HttpRequest processRequest(RequestHook<?, ?, HttpRequest> hook) {
            if (hook.operation().schema().hasTrait(CHECKSUM_REQUIRED_TRAIT_KEY)) {
                return addContentMd5Header(hook.request());
            }
            return hook.request();
        }

        static HttpRequest addContentMd5Header(HttpRequest request) {
            var body = request.body();
            if (body != null) {
                var buffer = body.waitForByteBuffer();
                var bytes = ByteBufferUtils.getBytes(buffer);
                try {
                    byte[] hash = MessageDigest.getInstance("MD5").digest(bytes);
                    String base64Hash = Base64.getEncoder().encodeToString(hash);
                    return request.toBuilder()
                            .withReplacedHeader("Content-MD5", ListUtils.of(base64Hash))
                            .build();
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Unable to fetch message digest instance for MD5", e);
                }
            }
            return request;
        }
    }
}
