/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.aws.traits.auth.SigV4ATrait;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.event.FrameProcessor;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides the AWS Signature Version 4a (SigV4a) auth scheme for HTTP requests.
 *
 * <p>SigV4a signs requests with an ECDSA P-256 key derived from the caller's {@link AwsCredentialsIdentity}.
 * Each signature is valid in every region named by the {@code X-Amz-Region-Set} header.
 * Add an instance to your client builder or apply the auth scheme to your Smithy service:
 * <pre>{@code
 * use aws.auth#sigv4a
 *
 * @sigv4a(name: "service")
 * service MyService
 * }</pre>
 *
 * <p>The signer checks these region set sources in order:
 * <ol>
 *     <li>The endpoint's {@code signingRegionSet}.</li>
 *     <li>The client setting {@link SigV4aSettings#SIGNING_REGION_SET}.</li>
 *     <li>The endpoint's {@code signingRegion}.</li>
 *     <li>The client region.</li>
 * </ol>
 *
 * <p>Signing requires no native library.
 *
 * <p>The client code generator requires the SigV4a auth scheme factory on its classpath
 * to add the scheme to generated clients.
 *
 * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-signing-elements.html">AWS signing</a>
 */
public final class SigV4aAuthScheme implements AuthScheme<HttpRequest, AwsCredentialsIdentity> {
    private final String signingName;
    private volatile SignerProperties signerProperties;

    public SigV4aAuthScheme(String signingName) {
        this.signingName = signingName;
    }

    @Override
    public ShapeId schemeId() {
        return SigV4ATrait.ID;
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
        var region = context.expect(SigV4Settings.REGION);
        var clock = context.get(SigV4Settings.CLOCK);
        var regionSet = context.get(SigV4aSettings.SIGNING_REGION_SET);
        var normalizePath = context.get(SigV4Settings.NORMALIZE_PATH);
        var doubleUrlEncode = context.get(SigV4Settings.DOUBLE_URL_ENCODE);
        var cached = signerProperties;
        if (cached != null
                && cached.region == region
                && cached.clock == clock
                && cached.regionSet == regionSet
                && Objects.equals(cached.normalizePath, normalizePath)
                && Objects.equals(cached.doubleUrlEncode, doubleUrlEncode)) {
            return cached.properties;
        }
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, signingName);
        ctx.put(SigV4Settings.REGION, region);
        if (clock != null) {
            ctx.put(SigV4Settings.CLOCK, clock);
        }
        if (regionSet != null) {
            ctx.put(SigV4aSettings.SIGNING_REGION_SET, regionSet);
        }
        if (normalizePath != null) {
            ctx.put(SigV4Settings.NORMALIZE_PATH, normalizePath);
        }
        if (doubleUrlEncode != null) {
            ctx.put(SigV4Settings.DOUBLE_URL_ENCODE, doubleUrlEncode);
        }
        var properties = Context.unmodifiableView(ctx);
        signerProperties = new SignerProperties(region, clock, regionSet, normalizePath, doubleUrlEncode, properties);
        return properties;
    }

    private record SignerProperties(
            String region,
            Clock clock,
            List<String> regionSet,
            Boolean normalizePath,
            Boolean doubleUrlEncode,
            Context properties) {}

    @Override
    public Signer<HttpRequest, AwsCredentialsIdentity> signer() {
        return SigV4aSigner.create();
    }

    @Override
    @SuppressWarnings("unchecked")
    public FrameProcessor<AwsEventFrame> eventSigner(
            AwsCredentialsIdentity identity,
            Context context,
            String seedSignature
    ) {
        return new SigV4aEventSigner(identity, context, seedSignature);
    }

    public static final class Factory implements AuthSchemeFactory<SigV4ATrait> {

        @Override
        public ShapeId schemeId() {
            return SigV4ATrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(SigV4ATrait trait) {
            return new SigV4aAuthScheme(trait.getName());
        }
    }
}
