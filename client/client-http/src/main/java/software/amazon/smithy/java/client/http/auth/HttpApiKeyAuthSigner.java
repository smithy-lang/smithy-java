/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.uri.QueryStringBuilder;
import software.amazon.smithy.java.logging.InternalLogger;

final class HttpApiKeyAuthSigner implements Signer<HttpRequest, ApiKeyIdentity> {
    static final HttpApiKeyAuthSigner INSTANCE = new HttpApiKeyAuthSigner();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpApiKeyAuthScheme.class);

    private HttpApiKeyAuthSigner() {}

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, ApiKeyIdentity identity, Context properties) {
        var name = properties.expect(HttpApiKeyAuthScheme.NAME);
        return switch (properties.expect(HttpApiKeyAuthScheme.IN)) {
            case HEADER -> {
                var schemeValue = properties.get(HttpApiKeyAuthScheme.SCHEME);
                var value = identity.apiKey();
                if (schemeValue != null) {
                    value = schemeValue + " " + value;
                }
                var mod = request.toModifiableCopy();
                if (mod.headers().hasHeader(name)) {
                    LOGGER.debug("Replaced header value for {}", name);
                }
                yield new SignResult<>(mod.setHeader(name, value));
            }
            case QUERY -> {
                var queryBuilder = new QueryStringBuilder();
                queryBuilder.add(name, identity.apiKey());
                var stringBuilder = new StringBuilder();
                var existingQuery = request.uri().getQuery();
                addExistingQueryParams(stringBuilder, existingQuery, name);
                queryBuilder.write(stringBuilder);
                var mod = request.toModifiableCopy();
                mod.setUri(request.uri().withQuery(stringBuilder.toString()));
                yield new SignResult<>(mod);
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
