/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static software.amazon.smithy.java.core.schema.TraitKey.CORS_TRAIT;

import java.util.*;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.traits.CorsTrait;

public final class CorsHeaders {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(CorsHeaders.class);

    private CorsHeaders() {} // Prevent instantiation

    public static Map<String, List<String>> of(HttpJob job) {
        if (!shouldAddCorsHeaders(job)) {
            return Map.of();
        }

        String requestOrigin = job.request().headers().firstValue("origin");
        Optional<String> configuredOrigin = getConfiguredOrigin(job);

        if (!isOriginAllowed(configuredOrigin.orElse(null), requestOrigin)) {
            return Map.of();
        }

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", List.of(requestOrigin));
        headers.put("Access-Control-Allow-Methods", List.of("GET, POST, PUT, DELETE, OPTIONS"));
        headers.put("Access-Control-Allow-Headers",
                List.of("*,Access-Control-Allow-Headers,Access-Control-Allow-Methods,Access-Control-Allow-Origin,Amz-Sdk-Invocation-Id,Amz-Sdk-Request,Authorization,Content-Length,Content-Type,X-Amz-User-Agent,X-Amzn-Trace-Id"));
        headers.put("Access-Control-Max-Age", List.of("600"));

        return headers;
    }

    private static boolean shouldAddCorsHeaders(HttpJob job) {
        if (job == null || job.operation() == null
                || job.operation().getApiOperation() == null
                ||
                job.operation().getApiOperation().service() == null
                ||
                job.operation().getApiOperation().service().schema() == null
                ||
                !job.operation().getApiOperation().service().schema().hasTrait(CORS_TRAIT)) {
            return false;
        }

        return job.request() != null
                && job.request().headers() != null
                && job.request().headers().hasHeader("origin");
    }

    private static Optional<String> getConfiguredOrigin(HttpJob job) {
        return Optional.ofNullable(job.operation()
                .getApiOperation()
                .service()
                .schema()
                .getTrait(CORS_TRAIT))
                .map(CorsTrait::getOrigin);
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
