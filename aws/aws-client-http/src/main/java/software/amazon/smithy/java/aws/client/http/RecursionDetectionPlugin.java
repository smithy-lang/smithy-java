/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import java.util.List;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Adds the "x-amz-trace-id" header when running in Lambda.
 *
 * @link <a href="https://docs.aws.amazon.com/lambda/latest/dg/invocation-recursion.html">Lambda documentation</a>
 */
public final class RecursionDetectionPlugin implements ClientPlugin {

    private final String traceId;

    public RecursionDetectionPlugin() {
        this(getTraceIdEnv());
    }

    // Mostly here for testing.
    RecursionDetectionPlugin(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (traceId != null) {
            config.addInterceptor(new Interceptor(List.of(traceId)));
        }
    }

    private static String getTraceIdEnv() {
        return System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null
                ? System.getenv("_X_AMZN_TRACE_ID")
                : null;
    }

    private record Interceptor(List<String> traceIdHeader) implements ClientInterceptor {
        @Override
        public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
            return hook.mapRequest(HttpRequest.class, h -> {
                if (h.request().headers().hasHeader("x-amzn-trace-id")) {
                    return h.request();
                } else {
                    return h.request().toBuilder().withReplacedHeader("x-amzn-trace-id", traceIdHeader).build();
                }
            });
        }
    }
}
