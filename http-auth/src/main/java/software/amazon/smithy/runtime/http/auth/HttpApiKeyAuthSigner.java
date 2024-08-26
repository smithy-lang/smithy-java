/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.runtime.core.uri.QueryStringBuilder;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpApiKeyAuthSigner implements Signer<SmithyHttpRequest, ApiKeyIdentity> {
    static final HttpApiKeyAuthSigner INSTANCE = new HttpApiKeyAuthSigner();

    private HttpApiKeyAuthSigner() {}

    @Override
    public SmithyHttpRequest sign(SmithyHttpRequest request, ApiKeyIdentity identity, AuthProperties properties) {
        var name = properties.expect(HttpApiKeyAuthScheme.NAME);
        return switch (properties.expect(HttpApiKeyAuthScheme.IN)) {
            case HEADER -> {
                var schemeValue = properties.get(HttpApiKeyAuthScheme.SCHEME);
                var value = identity.apiKey();
                // If the scheme value is not null prefix with scheme
                if (schemeValue != null) {
                    value = schemeValue + " " + value;
                }
                yield request.withAddedHeaders(name, value);
            }
            case QUERY -> {
                var uriBuilder = URIBuilder.of(request.uri());
                var queryBuilder = new QueryStringBuilder();
                queryBuilder.put(name, identity.apiKey());
                var stringBuilder = new StringBuilder();
                var existingQuery = request.uri().getQuery();
                if (existingQuery != null) {
                    stringBuilder.append(existingQuery);
                    stringBuilder.append('&');
                }
                queryBuilder.write(stringBuilder);
                yield request.withUri(uriBuilder.query(stringBuilder.toString()).build());
            }
        };
    }
}
