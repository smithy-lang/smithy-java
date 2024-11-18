/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import software.amazon.smithy.java.runtime.client.core.CallContext;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

/**
 * Adds the header "amz-sdk-request: ttl=X; attempt=Y; max=Z".
 */
public final class AmzSdkRequestPlugin implements ClientPlugin {

    private static final ClientInterceptor INTERCEPTOR = new Interceptor();

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.addInterceptor(INTERCEPTOR);
    }

    private static final class Interceptor implements ClientInterceptor {
        @Override
        public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
            return hook.mapRequest(SmithyHttpRequest.class, Mapper.INSTANCE);
        }
    }

    private static final class Mapper implements Function<RequestHook<?, ?, SmithyHttpRequest>, SmithyHttpRequest> {
        private static final Mapper INSTANCE = new Mapper();

        @Override
        public SmithyHttpRequest apply(RequestHook<?, ?, SmithyHttpRequest> hook) {
            var attempt = hook.context().get(CallContext.RETRY_ATTEMPT);
            if (attempt == null) {
                return hook.request();
            } else {
                var max = hook.context().get(CallContext.RETRY_MAX);
                StringBuilder value = new StringBuilder();
                value.append("attempt=").append(attempt);
                if (max != null) {
                    value.append("; max=").append(max);
                }

                // TODO: improve header management.
                var headers = new HashMap<>(hook.request().headers().map());
                headers.put("amz-sdk-request", List.of(value.toString()));
                return hook.request().withHeaders(HttpHeaders.of(headers));
            }
        }
    }
}
