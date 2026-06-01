/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.aws.traits.HttpChecksumTrait;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.AwsChunkedDataStream;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Frames the request body in {@code aws-chunked} content-encoding and emits a CRC32 checksum
 * trailer for S3 Express data-plane requests. Pairs with the unsigned-payload-trailer signing
 * mode so the SigV4 canonical request hashes the literal string
 * {@code STREAMING-UNSIGNED-PAYLOAD-TRAILER} instead of the body bytes.
 *
 * <p>Conditions for activation, all of which must hold:
 * <ul>
 *     <li>{@link S3ExpressContext#BUCKET} is set on the call context — i.e. this is an S3
 *         Express directory-bucket call.</li>
 *     <li>The operation declares the {@code aws.protocols#httpChecksum} trait with a
 *         {@code requestAlgorithmMember}.</li>
 *     <li>No {@code x-amz-checksum-*} or {@code x-amz-trailer} header is already present (the
 *         user, codec, or upstream interceptor did not pre-stamp a checksum).</li>
 *     <li>The request body is replayable and exposes a {@link java.nio.ByteBuffer}. Streaming
 *         bodies fall through unchanged.</li>
 * </ul>
 *
 * <p>Runs in {@code modifyBeforeSigning} so the framing-related headers
 * ({@code x-amz-trailer}, {@code x-amz-content-sha256}, {@code content-encoding},
 * {@code content-length}, {@code x-amz-decoded-content-length}) are part of the SigV4 canonical
 * request. The {@code x-amz-checksum-crc32} value itself is emitted at send time inside the body
 * bytes; the SigV4 signature only covers the trailer's <em>name</em> via {@code x-amz-trailer}.
 */
final class S3ExpressChecksumInterceptor implements ClientInterceptor {

    static final S3ExpressChecksumInterceptor INSTANCE = new S3ExpressChecksumInterceptor();

    private static final TraitKey<HttpChecksumTrait> HTTP_CHECKSUM_TRAIT = TraitKey.get(HttpChecksumTrait.class);
    private static final String CHECKSUM_HEADER_PREFIX = "x-amz-checksum-";
    private static final String SDK_CHECKSUM_ALGORITHM_HEADER = "x-amz-sdk-checksum-algorithm";
    private static final String TRAILER_HEADER = "x-amz-trailer";
    private static final String CONTENT_ENCODING_HEADER = "content-encoding";
    private static final String CONTENT_LENGTH_HEADER = "content-length";
    private static final String DECODED_CONTENT_LENGTH_HEADER = "x-amz-decoded-content-length";
    private static final String CHECKSUM_TRAILER_NAME = "x-amz-checksum-crc32";
    private static final String AWS_CHUNKED = "aws-chunked";
    private static final String STREAMING_UNSIGNED_PAYLOAD_TRAILER = "STREAMING-UNSIGNED-PAYLOAD-TRAILER";
    private static final String CRC32_NAME = "CRC32";
    private static final String CONTENT_SHA256_HEADER = "x-amz-content-sha256";

    private S3ExpressChecksumInterceptor() {}

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        if (hook.context().get(S3ExpressContext.BUCKET) == null) {
            return hook.request();
        }

        if (!(hook.request() instanceof HttpRequest req)) {
            return hook.request();
        }

        var trait = hook.operation().schema().getTrait(HTTP_CHECKSUM_TRAIT);
        if (trait == null || trait.getRequestAlgorithmMember().isEmpty()) {
            return hook.request();
        }

        if (alreadyHasChecksum(req)) {
            return hook.request();
        }

        DataStream body = req.body();
        if (body == null || body.contentLength() == 0) {
            return hook.request();
        }

        if (!body.isReplayable() || !body.hasByteBuffer()) {
            // Streaming or non-replayable body — supporting it requires multi-chunk framing,
            // which AwsChunkedDataStream doesn't do yet. Skip the wrapping; SigV4 will fall
            // back to body-SHA256 mode for now.
            return hook.request();
        }

        var chunked = new AwsChunkedDataStream(body);
        var modifiable = req.toModifiable();
        modifiable.setBody(chunked);
        var headers = modifiable.headers();
        headers.setHeader(SDK_CHECKSUM_ALGORITHM_HEADER, CRC32_NAME);
        headers.setHeader(CONTENT_ENCODING_HEADER, AWS_CHUNKED);
        headers.setHeader(DECODED_CONTENT_LENGTH_HEADER, Long.toString(chunked.decodedLength()));
        headers.setHeader(CONTENT_LENGTH_HEADER, Long.toString(chunked.contentLength()));
        headers.setHeader(TRAILER_HEADER, CHECKSUM_TRAILER_NAME);
        // Pre-set x-amz-content-sha256 with the unsigned-payload-trailer sentinel. SigV4Signer
        // detects this and uses the literal as the body-hash component of the canonical request
        // instead of computing SHA-256 of the body. That's the whole CPU win.
        headers.setHeader(CONTENT_SHA256_HEADER, STREAMING_UNSIGNED_PAYLOAD_TRAILER);

        return hook.asRequestType(modifiable);
    }

    private static boolean alreadyHasChecksum(HttpRequest req) {
        if (req.headers().hasHeader(TRAILER_HEADER)) {
            return true;
        }

        var present = new boolean[1];
        req.headers().forEachEntry(present, (state, name, value) -> {
            if (name.startsWith(CHECKSUM_HEADER_PREFIX)) {
                state[0] = true;
            }
        });

        return present[0];
    }
}
