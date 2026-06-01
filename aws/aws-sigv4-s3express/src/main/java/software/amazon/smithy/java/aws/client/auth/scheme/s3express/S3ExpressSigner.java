/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.util.Objects;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Signer for the S3 Express auth scheme (wire name {@code sigv4-s3express}, smithy-java id
 * {@code aws.auth#sigv4S3Express}).
 *
 * <p>Wraps a regular SigV4 signer with two adjustments mandated by the S3 Express SEP:
 *
 * <ul>
 *     <li>The session token from the {@link AwsCredentialsIdentity} is sent as
 *         {@code x-amz-s3session-token}, not {@code x-amz-security-token}.</li>
 *     <li>{@code x-amz-security-token} is never set, even when the identity carries a
 *         non-null session token, since S3 Express credentials are not STS credentials.</li>
 * </ul>
 *
 * <p>The header is added to the request before the underlying SigV4 signer runs, so the
 * canonical request includes it and the wire signature covers it.
 */
final class S3ExpressSigner implements Signer<HttpRequest, AwsCredentialsIdentity> {

    static final String SESSION_HEADER = "x-amz-s3session-token";

    private final Signer<HttpRequest, AwsCredentialsIdentity> delegate;

    static S3ExpressSigner create(String signingName) {
        // Going through SigV4AuthScheme.signer() rather than reaching into the package-private
        // SigV4Signer directly. The auth scheme constructor only carries the signing name; the
        // pipeline merges signingName/signingRegion overrides from EndpointAuthSchemeSettings
        // onto the signer's Context, so passing an arbitrary name here is fine.
        return new S3ExpressSigner(new SigV4AuthScheme(signingName).signer());
    }

    private S3ExpressSigner(Signer<HttpRequest, AwsCredentialsIdentity> delegate) {
        this.delegate = delegate;
    }

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        // 1. Add the session token to the request as x-amz-s3session-token. Doing this before
        //    delegating means the SigV4 signer sees it among the request headers and includes it
        //    in the canonical request, so the signature covers the session token.
        var modified = request.toModifiable();
        modified.headers()
                .setHeader(
                        SESSION_HEADER,
                        Objects.requireNonNull(identity.sessionToken(), "S3 Express session token"));

        // 2. Tell the regular SigV4 signer not to emit x-amz-security-token. The same token is
        //    already on the request as x-amz-s3session-token and will be signed there.
        Context signingProperties = properties;
        if (!properties.getOrDefault(SigV4Settings.OMIT_SECURITY_TOKEN, false)) {
            signingProperties = Context.modifiableCopy(properties).put(SigV4Settings.OMIT_SECURITY_TOKEN, true);
        }

        return delegate.sign(modified, identity, signingProperties);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
