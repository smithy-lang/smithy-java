/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import java.util.Objects;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

/**
 * Implements HTTP-specific authentication using an API key sent in a header or query string parameter.
 */
public final class HttpApiKeyAuthScheme implements AuthScheme<HttpRequest, ApiKeyIdentity> {
    static final Context.Key<String> NAME = Context.key(
            "Name of the header or query parameter that contains the API key");
    static final Context.Key<HttpApiKeyAuthTrait.Location> IN = Context.key(
            "Defines the location of where the key is serialized.");
    static final Context.Key<String> SCHEME = Context.key(
            "Defines the IANA scheme to use on the Authorization header value.");

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
    public Class<HttpRequest> requestClass() {
        return HttpRequest.class;
    }

    @Override
    public Class<ApiKeyIdentity> identityClass() {
        return ApiKeyIdentity.class;
    }

    @Override
    public Context getSignerProperties(Context context) {
        var ctx = Context.create();
        ctx.put(IN, in);
        ctx.put(NAME, name);
        if (scheme != null) {
            ctx.put(SCHEME, scheme);
        }
        return Context.unmodifiableView(ctx);
    }

    @Override
    public Signer<HttpRequest, ApiKeyIdentity> signer() {
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
