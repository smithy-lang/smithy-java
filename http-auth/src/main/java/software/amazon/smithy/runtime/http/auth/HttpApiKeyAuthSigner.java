/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import java.util.Objects;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.core.uri.QueryStringBuilder;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpApiKeyAuthSigner implements Signer<SmithyHttpRequest, TokenIdentity> {
    public static final HttpApiKeyAuthSigner INSTANCE = new HttpApiKeyAuthSigner();
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private HttpApiKeyAuthSigner() {}

    @Override
    public SmithyHttpRequest sign(SmithyHttpRequest request, TokenIdentity identity, AuthProperties properties) {
        var name = Objects.requireNonNull(properties.get(HttpApiKeyAuthScheme.NAME));
        return switch (Objects.requireNonNull(properties.get(HttpApiKeyAuthScheme.IN))) {
            case HEADER -> // TODO: handle authorization header value?
                request.withAddedHeaders(name, identity.token());
            case QUERY -> {
                var uriBuilder = URIBuilder.of(request.uri());
                var queryBuilder = new QueryStringBuilder();
                queryBuilder.put(name, identity.token());
                var stringBuilder = new StringBuilder();
                stringBuilder.append(request.uri().getQuery());
                stringBuilder.append('&');
                queryBuilder.write(stringBuilder);
                yield request.withUri(uriBuilder.query(stringBuilder.toString()).build());
            }
        };
    }
}
