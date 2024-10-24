/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.io.uri.QueryStringBuilder;
import software.amazon.smithy.java.runtime.io.uri.URIBuilder;

final class HttpApiKeyAuthSigner implements Signer<SmithyHttpRequest, ApiKeyIdentity> {
    static final HttpApiKeyAuthSigner INSTANCE = new HttpApiKeyAuthSigner();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpApiKeyAuthScheme.class);

    private HttpApiKeyAuthSigner() {}

    @Override
    public CompletableFuture<SmithyHttpRequest> sign(
        SmithyHttpRequest request,
        ApiKeyIdentity identity,
        AuthProperties properties
    ) {
        var name = properties.expect(HttpApiKeyAuthScheme.NAME);
        return switch (properties.expect(HttpApiKeyAuthScheme.IN)) {
            case HEADER -> {
                var schemeValue = properties.get(HttpApiKeyAuthScheme.SCHEME);
                var value = identity.apiKey();
                // If the scheme value is not null prefix with scheme
                if (schemeValue != null) {
                    value = schemeValue + " " + value;
                }
                var updated = new LinkedHashMap<>(request.headers().map());
                var existing = updated.put(name, List.of(value));
                if (existing != null) {
                    LOGGER.debug("Replaced header value for {}", name);
                }
                yield CompletableFuture.completedFuture(request.withHeaders(HttpHeaders.of(updated)));
            }
            case QUERY -> {
                var uriBuilder = URIBuilder.of(request.uri());
                var queryBuilder = new QueryStringBuilder();
                queryBuilder.add(name, identity.apiKey());
                var stringBuilder = new StringBuilder();
                var existingQuery = request.uri().getQuery();
                addExistingQueryParams(stringBuilder, existingQuery, name);
                queryBuilder.write(stringBuilder);
                yield CompletableFuture.completedFuture(
                    request.withUri(uriBuilder.query(stringBuilder.toString()).build())
                );
            }
        };
    }

    private static void addExistingQueryParams(StringBuilder stringBuilder, String existingQuery, String name) {
        if (existingQuery == null) {
            return;
        }
        for (var query : existingQuery.split("&")) {
            if (!query.startsWith(name + "=")) {
                stringBuilder.append(query);
                stringBuilder.append('&');
            } else {
                LOGGER.debug("Removing conflicting query param for `{}`", name);
            }
        }
    }
}
