/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.AuthProperty;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

/**
 * Implements HTTP-specific authentication using an API key sent in a header or query string parameter.
 */
public final class HttpApiKeyAuthScheme implements AuthScheme<SmithyHttpRequest, ApiKeyIdentity> {
    static final AuthProperty<String> NAME = AuthProperty.of(
        "Name of the header or query parameter that contains the API key"
    );
    static final AuthProperty<HttpApiKeyAuthTrait.Location> IN = AuthProperty.of(
        "Defines the location of where the key is serialized."
    );
    static final AuthProperty<String> SCHEME = AuthProperty.of(
        "Defines the IANA scheme to use on the Authorization header value."
    );

    private final String scheme;
    private final String name;
    private final HttpApiKeyAuthTrait.Location in;

    public HttpApiKeyAuthScheme(String name, HttpApiKeyAuthTrait.Location in, String scheme) {
        this.name = Objects.requireNonNull(name, "name cannot be null.");
        this.in = Objects.requireNonNull(in, "in cannot be null.");
        this.scheme = scheme;
    }

    @Override
    public ShapeId schemeId() {
        return HttpApiKeyAuthTrait.ID;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<ApiKeyIdentity> identityClass() {
        return ApiKeyIdentity.class;
    }

    @Override
    public AuthProperties getSignerProperties(Context context) {
        var builder = AuthProperties.builder();
        builder.put(IN, in);
        builder.put(NAME, name);
        if (scheme != null) {
            builder.put(SCHEME, scheme);
        }
        return builder.build();
    }

    @Override
    public Signer<SmithyHttpRequest, ApiKeyIdentity> signer() {
        return HttpApiKeyAuthSigner.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<HttpApiKeyAuthTrait> {

        @Override
        public ShapeId schemeId() {
            return HttpApiKeyAuthTrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(HttpApiKeyAuthTrait trait) {
            return new HttpApiKeyAuthScheme(trait.getName(), trait.getIn(), trait.getScheme().orElse(null));
        }
    }
}
