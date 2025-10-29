/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.security.MessageDigest;
import java.util.Base64;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.http.api.HttpRequest;
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

        @Override
        public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
            return hook.mapRequest(HttpRequest.class, h -> {
                var operation = h.operation();
                if (operation.schema().getTrait(TraitKey.get(HttpChecksumRequiredTrait.class)) != null) {
                    return addContentMd5Header(h.request());
                }
                return h.request();
            });
        }

        HttpRequest addContentMd5Header(HttpRequest request) {
            try {
                var body = request.body();
                if (body != null) {
                    var buffer = body.waitForByteBuffer();
                    var bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    byte[] hash = md5.digest(bytes);
                    String base64Hash = Base64.getEncoder().encodeToString(hash);

                    return request.toBuilder()
                            .withReplacedHeader("Content-MD5", ListUtils.of(base64Hash))
                            .build();
                }
                return request;
            } catch (Exception e) {
                throw new RuntimeException("Failed to calculate MD5 checksum", e);
            }
        }
    }
}
