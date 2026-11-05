/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.event.FrameProcessor;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Chains AWS event stream signatures from the initial HTTP request signature.
 *
 * <p>Each signed frame carries the original message as its payload and two headers:
 * <ul>
 *   <li>{@code :date} - The signing timestamp</li>
 *   <li>{@code :chunk-signature} - The binary signature for this frame</li>
 * </ul>
 *
 * <p>Frames must use the same signing name and region as the HTTP request that opened the stream,
 * including endpoint overrides.
 */
abstract sealed class AwsEventSigner implements FrameProcessor<AwsEventFrame>
        permits SigV4EventSigner, SigV4aEventSigner {
    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    final AwsCredentialsIdentity identity;
    final Context signerContext;
    private final String algorithm;
    private String previousSignature;

    /**
     * @param identity      the AWS credentials to use for signing
     * @param signerContext the signing context containing region, service name, and optional clock
     * @param seedSignature the hex-encoded signature from the initial HTTP request
     */
    AwsEventSigner(String algorithm, AwsCredentialsIdentity identity, Context signerContext, String seedSignature) {
        this.algorithm = algorithm;
        this.identity = identity;
        this.signerContext = signerContext;
        this.previousSignature = stripPadding(Objects.requireNonNull(seedSignature, "seedSignature"));
    }

    /** The region component of the credential scope, or {@code null} when the algorithm omits it. */
    abstract String scopeRegion();

    abstract byte[] sign(byte[] stringToSign, String region, String service, SigningResources resources);

    @Override
    public AwsEventFrame transformFrame(AwsEventFrame frame) {
        var message = frame.unwrap();
        return signAndFrameChunk(ByteBufferUtils.getBytes(message.toByteBuffer()));
    }

    @Override
    public AwsEventFrame closingFrame() {
        return signAndFrameChunk(EMPTY_PAYLOAD);
    }

    AwsEventFrame signAndFrameChunk(byte[] payload) {
        var resources = SigningResources.get();
        try {
            var instant = SigningParameters.clock(signerContext).instant();
            var binarySignature = signChunk(payload, instant, resources);
            previousSignature = HexFormat.of().formatHex(binarySignature);
            return new AwsEventFrame(buildSignedMessage(payload, binarySignature, instant));
        } finally {
            SigningResources.release(resources);
        }
    }

    private byte[] signChunk(byte[] payload, Instant instant, SigningResources resources) {
        var region = scopeRegion();
        var service = SigningParameters.signingName(signerContext);
        resources.timestamp(instant.getEpochSecond());

        var sb = resources.sb;
        sb.setLength(0);
        sb.append(resources.dateStamp).append('/');
        if (region != null) {
            sb.append(region).append('/');
        }
        sb.append(service).append('/').append(HttpSigning.TERMINATOR);
        var scope = sb.toString();

        var stringToSign = buildStringToSign(resources.requestTime, scope, payload, instant, resources);
        return sign(stringToSign.getBytes(StandardCharsets.UTF_8), region, service, resources);
    }

    private String buildStringToSign(
            String dateTime,
            String scope,
            byte[] payload,
            Instant instant,
            SigningResources resources
    ) {
        var nonSigHeadersHash = HexFormat.of().formatHex(resources.hash(encodedDateHeader(instant)));
        var payloadHash = HexFormat.of().formatHex(resources.hash(payload));

        var sb = resources.sb;
        sb.setLength(0);
        sb.append(algorithm)
                .append('\n')
                .append(dateTime)
                .append('\n')
                .append(scope)
                .append('\n')
                .append(previousSignature)
                .append('\n')
                .append(nonSigHeadersHash)
                .append('\n')
                .append(payloadHash);
        return sb.toString();
    }

    private static byte[] encodedDateHeader(Instant instant) {
        return Message.encodeHeaders(Map.of(":date", HeaderValue.fromTimestamp(instant)).entrySet());
    }

    /**
     * Chunked SigV4a signatures may contain trailing {@code '*'} padding.
     * The signature chain requires the unpadded hex value.
     */
    private static String stripPadding(String signature) {
        int end = signature.length();
        while (end > 0 && signature.charAt(end - 1) == '*') {
            end--;
        }
        return end == signature.length() ? signature : signature.substring(0, end);
    }

    private static Message buildSignedMessage(byte[] originalMessage, byte[] binarySignature, Instant instant) {
        // Preserve order: :chunk-signature must always be the last header.
        var headers = new LinkedHashMap<String, HeaderValue>();
        headers.put(":date", HeaderValue.fromTimestamp(instant));
        headers.put(":chunk-signature", HeaderValue.fromByteArray(binarySignature));
        return new Message(headers, originalMessage);
    }
}
