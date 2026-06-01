/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Auth scheme for S3 Express directory-bucket data-plane requests.
 *
 * <p>The identity is bucket-scoped session credentials returned by S3's {@code CreateSession}
 * API; the signer uses regular SigV4 with the session token sent as
 * {@code x-amz-s3session-token} (instead of {@code x-amz-security-token}).
 */
final class S3ExpressAuthScheme implements AuthScheme<HttpRequest, AwsCredentialsIdentity> {

    static final ShapeId SCHEME_ID = ShapeId.from("aws.auth#sigv4S3express");

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
    S3ExpressAuthScheme(CreateSessionCallback createSession) {
        this(DEFAULT_SIGNING_NAME, createSession);
    }

    S3ExpressAuthScheme(String signingName, CreateSessionCallback createSession) {
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
    public Class<AwsCredentialsIdentity> identityClass() {
        return AwsCredentialsIdentity.class;
    }

    @Override
    public Context getSignerProperties(Context context) {
        // The pipeline still merges EndpointAuthSchemeSettings.SIGNING_NAME / SIGNING_REGION
        // overrides from the resolved endpoint on top of these. The values stamped here are the
        // defaults the signer falls back to when the endpoint didn't override.
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, signingName);
        ctx.put(SigV4Settings.OMIT_SECURITY_TOKEN, true);
        ctx.put(RegionSetting.REGION, context.expect(RegionSetting.REGION));
        var clock = context.get(SigV4Settings.CLOCK);
        if (clock != null) {
            ctx.put(SigV4Settings.CLOCK, clock);
        }
        return Context.unmodifiableView(ctx);
    }

    @Override
    public Context getIdentityProperties(Context context) {
        // The S3ExpressIdentityProvider needs to know the bucket to scope CreateSession + cache
        // lookup. The pipeline calls getIdentityProperties() with the call's Context, so we copy
        // BUCKET through into the per-resolveIdentity properties bag.
        var bucket = context.get(S3ExpressContext.BUCKET);
        if (bucket == null) {
            return Context.empty();
        }
        // Publish the S3_EXPRESS_BUCKET feature ID directly on the call context here. This hook
        // runs only when the s3express auth scheme is actually being prepared for the call, so
        // it's the right "this is an S3 Express request" signal. The UserAgentInterceptor reads
        // FEATURE_IDS later (in modifyBeforeTransmit) and emits "m/J".
        var ids = context.get(software.amazon.smithy.java.client.core.CallContext.FEATURE_IDS);
        if (ids != null) {
            ids.add(S3ExpressFeatureId.S3_EXPRESS_BUCKET);
        }
        var ctx = Context.create();
        ctx.put(S3ExpressContext.BUCKET, bucket);
        return Context.unmodifiableView(ctx);
    }

    @Override
    public Signer<HttpRequest, AwsCredentialsIdentity> signer() {
        return S3ExpressSigner.create(signingName);
    }

    @Override
    public IdentityResolver<AwsCredentialsIdentity> identityResolver(IdentityResolvers resolvers) {
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
