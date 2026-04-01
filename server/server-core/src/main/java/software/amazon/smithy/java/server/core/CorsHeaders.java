/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static software.amazon.smithy.java.core.schema.TraitKey.CORS_TRAIT;

import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.http.api.HeaderName;

public final class CorsHeaders {

    private static final HeaderName ACCESS_CONTROL_ALLOW_METHODS = HeaderName.of("access-control-allow-methods");
    private static final HeaderName ACCESS_CONTROL_ALLOW_HEADERS = HeaderName.of("access-control-allow-headers");
    private static final HeaderName ACCESS_CONTROL_MAX_AGE = HeaderName.of("access-control-max-age");

    private CorsHeaders() {}

    public static void addCorsHeaders(HttpJob job) {
        if (!shouldAddCorsHeaders(job)) {
            return;
        }

        String requestOrigin = job.request().headers().firstValue(HeaderName.ORIGIN);
        String configuredOrigin = getConfiguredOrigin(job);

        if (!isOriginAllowed(configuredOrigin, requestOrigin)) {
            return;
        }

        var headers = job.response().headers();
        headers.addHeader(ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        headers.addHeader(ACCESS_CONTROL_ALLOW_HEADERS,
                "*,Access-Control-Allow-Headers,Access-Control-Allow-Methods,Access-Control-Allow-Origin,Amz-Sdk-Invocation-Id,Amz-Sdk-Request,Authorization,Content-Length,Content-Type,X-Amz-User-Agent,X-Amzn-Trace-Id");
        headers.addHeader(ACCESS_CONTROL_MAX_AGE, "600");
        headers.addHeader(HeaderName.ACCESS_CONTROL_ALLOW_ORIGIN, List.of(requestOrigin));
    }

    private static boolean shouldAddCorsHeaders(HttpJob job) {
        if (job.operation().getApiOperation().service() == null ||
                job.operation().getApiOperation().service().schema() == null
                ||
                !job.operation().getApiOperation().service().schema().hasTrait(CORS_TRAIT)) {
            return false;
        }
        return job.request().headers().hasHeader(HeaderName.ORIGIN);
    }

    private static String getConfiguredOrigin(HttpJob job) {
        var corsTrait = job.operation()
                .getApiOperation()
                .service()
                .schema()
                .getTrait(CORS_TRAIT);
        if (corsTrait != null) {
            return corsTrait.getOrigin();
        }
        return null;
    }

    private static boolean isOriginAllowed(String configuredOrigin, String requestOrigin) {
        if (configuredOrigin == null || requestOrigin == null) {
            return false;
        }

        if (configuredOrigin.equals("*")) {
            return true;
        }

        return Arrays.stream(configuredOrigin.split(","))
                .map(String::trim)
                .anyMatch(origin -> origin.equalsIgnoreCase(requestOrigin));
    }
}
