/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.zip.CRC32;
import software.amazon.smithy.aws.traits.HttpChecksumTrait;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Stamps a CRC32 flexible checksum on S3 Express data-plane requests when the user did not
 * already specify one.
 *
 * <p>Conditions for activation, all of which must hold:
 * <ul>
 *     <li>{@link S3ExpressContext#BUCKET} is set on the call context — i.e. this is an S3
 *         Express directory-bucket call.</li>
 *     <li>The operation declares the {@code aws.protocols#httpChecksum} trait with a
 *         {@code requestAlgorithmMember}.</li>
 *     <li>No {@code x-amz-checksum-*} or {@code x-amz-trailer} header is already present (the
 *         user, codec, or upstream interceptor did not pre-stamp a checksum).</li>
 *     <li>The request body is replayable and exposes a {@link ByteBuffer}. Streaming bodies are
 *         intentionally skipped — supporting them requires {@code aws-chunked} encoding plus
 *         trailer support, which is a separate work item.</li>
 * </ul>
 *
 * <p>Runs in {@code modifyBeforeSigning} so the {@code x-amz-checksum-crc32} header is included
 * in the SigV4 canonical request and signed as part of the request.
 */
final class S3ExpressChecksumInterceptor implements ClientInterceptor {

    static final S3ExpressChecksumInterceptor INSTANCE = new S3ExpressChecksumInterceptor();

    private static final TraitKey<HttpChecksumTrait> HTTP_CHECKSUM_TRAIT = TraitKey.get(HttpChecksumTrait.class);
    private static final String CHECKSUM_HEADER_PREFIX = "x-amz-checksum-";
    private static final String SDK_CHECKSUM_ALGORITHM_HEADER = "x-amz-sdk-checksum-algorithm";
    private static final String CHECKSUM_CRC32_HEADER = "x-amz-checksum-crc32";
    private static final String TRAILER_HEADER = "x-amz-trailer";
    private static final String CRC32_NAME = "CRC32";

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
            // Streaming or non-replayable body — needs aws-chunked + trailers, not implemented yet.
            return hook.request();
        }

        ByteBuffer buf = body.asByteBuffer();
        var crc = new CRC32();
        crc.update(buf);
        long value = crc.getValue();

        // 4-byte big-endian, then base64.
        byte[] crcBytes = {
                (byte) ((value >>> 24) & 0xff),
                (byte) ((value >>> 16) & 0xff),
                (byte) ((value >>> 8) & 0xff),
                (byte) (value & 0xff)};
        String crc32B64 = Base64.getEncoder().encodeToString(crcBytes);

        var modifiable = req.toModifiable();
        modifiable.headers().setHeader(SDK_CHECKSUM_ALGORITHM_HEADER, CRC32_NAME);
        modifiable.headers().setHeader(CHECKSUM_CRC32_HEADER, crc32B64);
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
