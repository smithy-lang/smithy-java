/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.http.api.HeaderNames;
import software.amazon.smithy.java.http.api.HttpRequest;

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
            if (hook.request() instanceof HttpRequest req) {
                var attempt = hook.context().get(CallContext.RETRY_ATTEMPT);
                if (attempt != null) {
                    var max = hook.context().get(CallContext.RETRY_MAX);
                    StringBuilder value = new StringBuilder();
                    value.append("attempt=").append(attempt);
                    if (max != null) {
                        value.append("; max=").append(max);
                    }
                    return hook.asRequestType(
                            req.toModifiable()
                                    .setHeader(HeaderNames.AMZ_SDK_REQUEST, value.toString()));
                }
            }
            return hook.request();
        }
    }
}
