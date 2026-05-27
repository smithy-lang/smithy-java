/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Signer for the S3 Express auth scheme (wire name {@code sigv4-s3express}, smithy-java id
 * {@code aws.auth#sigv4S3Express}).
 *
 * <p>Wraps a regular SigV4 signer with two adjustments mandated by the S3 Express SEP:
 *
 * <ul>
 *     <li>The session token from the {@link S3ExpressIdentity} is sent as
 *         {@code x-amz-s3session-token}, not {@code x-amz-security-token}.</li>
 *     <li>{@code x-amz-security-token} is never set, even when the identity carries a
 *         non-null session token, since S3 Express credentials are not STS credentials.</li>
 * </ul>
 *
 * <p>The header is added to the request before the underlying SigV4 signer runs, so the
 * canonical request includes it and the wire signature covers it.
 */
final class S3ExpressSigner implements Signer<HttpRequest, S3ExpressIdentity> {

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
    public SignResult<HttpRequest> sign(HttpRequest request, S3ExpressIdentity identity, Context properties) {
        // 1. Add the session token to the request as x-amz-s3session-token. Doing this before
        //    delegating means the SigV4 signer sees it among the request headers and includes it
        //    in the canonical request, so the signature covers the session token.
        var modified = request.toModifiable();
        modified.headers().setHeader(SESSION_HEADER, identity.sessionToken());

        // 2. Hand the SigV4 signer an identity *without* a sessionToken. SigV4Signer will then
        //    skip writing x-amz-security-token (per its own logic), which is exactly what the
        //    SEP requires for S3 Express requests.
        var withoutSessionToken = new TokenStrippedIdentity(identity);
        return delegate.sign(modified, withoutSessionToken, properties);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private record TokenStrippedIdentity(AwsCredentialsIdentity wrapped) implements AwsCredentialsIdentity {
        @Override
        public String accessKeyId() {
            return wrapped.accessKeyId();
        }

        @Override
        public String secretAccessKey() {
            return wrapped.secretAccessKey();
        }

        @Override
        public String sessionToken() {
            return null;
        }
    }
}
