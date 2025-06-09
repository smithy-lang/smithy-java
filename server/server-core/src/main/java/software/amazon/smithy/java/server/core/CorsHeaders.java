/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static software.amazon.smithy.java.core.schema.TraitKey.CORS_TRAIT;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class CorsHeaders {

    private CorsHeaders() {}

    private static final Map<String, List<String>> BASE_CORS_HEADERS = Map.of(
            "Access-Control-Allow-Methods",
            List.of("GET, POST, PUT, DELETE, OPTIONS"),
            "Access-Control-Allow-Headers",
            List.of("*,Access-Control-Allow-Headers,Access-Control-Allow-Methods,Access-Control-Allow-Origin,Amz-Sdk-Invocation-Id,Amz-Sdk-Request,Authorization,Content-Length,Content-Type,X-Amz-User-Agent,X-Amzn-Trace-Id"),
            "Access-Control-Max-Age",
            List.of("600"));

    public static void addCorsHeaders(HttpJob job) {
        if (!shouldAddCorsHeaders(job)) {
            return;
        }

        String requestOrigin = job.request().headers().firstValue("origin");
        String configuredOrigin = getConfiguredOrigin(job);

        if (!isOriginAllowed(configuredOrigin, requestOrigin)) {
            return;
        }

        job.response().headers().putHeaders(BASE_CORS_HEADERS);
        job.response().headers().putHeader("Access-Control-Allow-Origin", List.of(requestOrigin));
    }

    private static boolean shouldAddCorsHeaders(HttpJob job) {
        if (job.operation().getApiOperation().service() == null ||
                job.operation().getApiOperation().service().schema() == null
                ||
                !job.operation().getApiOperation().service().schema().hasTrait(CORS_TRAIT)) {
            return false;
        }
        return job.request().headers().hasHeader("origin");
    }

    private static String getConfiguredOrigin(HttpJob job) {
        var corsTrait = job.operation().getApiOperation()
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
