/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * The {@code aws.auth#sigv4-s3express} auth scheme.
 *
 * <p>Used for S3 Express directory-bucket data-plane requests. The identity is bucket-scoped
 * session credentials returned by S3's {@code CreateSession} API; the signer uses regular SigV4
 * with the session token sent as {@code x-amz-s3session-token} (instead of
 * {@code x-amz-security-token}).
 */
public final class S3ExpressAuthScheme implements AuthScheme<HttpRequest, S3ExpressIdentity> {

    public static final ShapeId SCHEME_ID = ShapeId.from("aws.auth#sigv4-s3express");

    private static final String DEFAULT_SIGNING_NAME = "s3express";

    private final String signingName;
    private final CreateSessionCallback createSession;
    // Cache the per-base-resolver provider so the bucket-keyed identity cache survives across
    // ClientPipeline calls. Without this, every ClientPipeline.createResolvedSchema would build
    // a fresh provider with a fresh cache, defeating the entire point.
    private volatile S3ExpressIdentityProvider provider;
    private volatile IdentityResolver<AwsCredentialsIdentity> providerBase;

    /**
     * @param createSession bridge to the user's S3 client's CreateSession operation.
     */
    public S3ExpressAuthScheme(CreateSessionCallback createSession) {
        this(DEFAULT_SIGNING_NAME, createSession);
    }

    public S3ExpressAuthScheme(String signingName, CreateSessionCallback createSession) {
        this.signingName = signingName;
        this.createSession = createSession;
    }

    @Override
    public ShapeId schemeId() {
        return SCHEME_ID;
    }

    @Override
    public Class<HttpRequest> requestClass() {
        return HttpRequest.class;
    }

    @Override
    public Class<S3ExpressIdentity> identityClass() {
        return S3ExpressIdentity.class;
    }

    @Override
    public Context getSignerProperties(Context context) {
        // The pipeline still merges EndpointAuthSchemeSettings.SIGNING_NAME / SIGNING_REGION
        // overrides from the resolved endpoint on top of these. The values stamped here are the
        // defaults the signer falls back to when the endpoint didn't override.
        var ctx = Context.create();
        ctx.put(software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings.SIGNING_NAME, signingName);
        ctx.put(RegionSetting.REGION, context.expect(RegionSetting.REGION));
        var clock = context.get(software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings.CLOCK);
        if (clock != null) {
            ctx.put(software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings.CLOCK, clock);
        }
        return Context.unmodifiableView(ctx);
    }

    @Override
    public Signer<HttpRequest, S3ExpressIdentity> signer() {
        return S3ExpressSigner.create(signingName);
    }

    @Override
    public IdentityResolver<S3ExpressIdentity> identityResolver(IdentityResolvers resolvers) {
        IdentityResolver<AwsCredentialsIdentity> base = resolvers.identityResolver(AwsCredentialsIdentity.class);
        if (base == null) {
            return null;
        }
        // Lock-free fast path for the common case where the same IdentityResolvers is passed
        // every call. If the base resolver instance changes (rare; it's a property of the
        // client's IdentityResolvers registry which doesn't change after build), rebuild.
        S3ExpressIdentityProvider local = provider;
        if (local != null && providerBase == base) {
            return local;
        }
        synchronized (this) {
            if (provider == null || providerBase != base) {
                providerBase = base;
                provider = new S3ExpressIdentityProvider(base, createSession);
            }
            return provider;
        }
    }
}
