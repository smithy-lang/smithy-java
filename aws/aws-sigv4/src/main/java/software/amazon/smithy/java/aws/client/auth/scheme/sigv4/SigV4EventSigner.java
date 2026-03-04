/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.event.FrameProcessor;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Signs AWS event stream frames using AWS Signature Version 4.
 * <p>
 * This signer implements the AWS4-HMAC-SHA256-PAYLOAD algorithm for event stream signing,
 * which chains signatures across frames. Each frame's signature depends on the previous
 * frame's signature, ensuring message ordering and integrity.
 * <p>
 * The signer crates a new frame with the original message as payload and two headers:
 * <ul>
 *   <li>{@code :date} - The signing timestamp</li>
 *   <li>{@code :chunk-signature} - The binary signature for this frame</li>
 * </ul>
 */
class SigV4EventSigner implements FrameProcessor<AwsEventFrame> {
    private static final String ALGORITHM = "AWS4-HMAC-SHA256-PAYLOAD";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    private final AwsCredentialsIdentity identity;
    private final Context signerContext;
    private String previousSignature;

    /**
     * Creates a new event stream signer.
     *
     * @param identity      the AWS credentials to use for signing
     * @param signerContext the signing context containing region, service name, and optional clock
     * @param seedSignature the hex-encoded signature from the initial HTTP request
     */
    public SigV4EventSigner(AwsCredentialsIdentity identity, Context signerContext, String seedSignature) {
        this.identity = identity;
        this.signerContext = signerContext;
        this.previousSignature = seedSignature;
    }

    @Override
    public AwsEventFrame transformFrame(AwsEventFrame frame) {
        var message = frame.unwrap();
        var payload = ByteBufferUtils.getBytes(message.toByteBuffer());
        return signAndFrameChunk(payload);
    }

    public AwsEventFrame signAndFrameChunk(byte[] payload) {
        var signingResources = SigningResources.get();
        try {
            signingResources.reset();
            var clock = signerContext.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());
            var instant = clock.instant();

            var binarySignature = signChunk(payload, previousSignature, instant, signingResources);

            // Store hex signature for next iteration
            previousSignature = HexFormat.of().formatHex(binarySignature);

            return new AwsEventFrame(buildSignedMessage(payload, binarySignature, instant));
        } finally {
            SigningResources.release(signingResources);
        }
    }

    @Override
    public AwsEventFrame closingFrame() {
        return signAndFrameChunk(EMPTY_PAYLOAD);
    }

    private byte[] signChunk(byte[] chunkBody, String prevSignature, Instant instant, SigningResources resources) {
        var region = signerContext.expect(SigV4Settings.REGION);
        var service = signerContext.expect(SigV4Settings.SIGNING_NAME);

        var dateTime = instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
        var sb = resources.sb;
        sb.setLength(0);
        sb.append(dateTime.getYear());
        appendTwoDigits(dateTime.getMonthValue(), sb);
        appendTwoDigits(dateTime.getDayOfMonth(), sb);
        var dateStamp = sb.toString();

        sb.setLength(0);
        sb.append(dateStamp).append('T');
        appendTwoDigits(dateTime.getHour(), sb);
        appendTwoDigits(dateTime.getMinute(), sb);
        appendTwoDigits(dateTime.getSecond(), sb);
        sb.append('Z');
        var requestTime = sb.toString();

        sb.setLength(0);
        sb.append(dateStamp).append('/').append(region).append('/').append(service).append('/').append(TERMINATOR);
        var scope = sb.toString();

        var stringToSign = buildStringToSign(requestTime, scope, prevSignature, chunkBody, instant, resources);
        var signingKey = deriveSigningKey(identity.secretAccessKey(), dateStamp, region, service, resources);

        return sign(stringToSign, signingKey, resources);
    }

    private static void appendTwoDigits(int value, StringBuilder sb) {
        if (value < 10) {
            sb.append('0');
        }
        sb.append(value);
    }

    private String buildStringToSign(
            String dateTime,
            String scope,
            String prevSigHex,
            byte[] payload,
            Instant instant,
            SigningResources resources
    ) {
        var nonSigHeadersHash = eventStreamNonSignatureHeadersHash(instant, resources);
        var payloadHash = HexFormat.of().formatHex(hash(payload, resources));

        var sb = resources.sb;
        sb.setLength(0);
        sb.append(ALGORITHM)
                .append('\n')
                .append(dateTime)
                .append('\n')
                .append(scope)
                .append('\n')
                .append(prevSigHex)
                .append('\n')
                .append(nonSigHeadersHash)
                .append('\n')
                .append(payloadHash);

        var toSign = resources.sb.toString();
        return toSign;
    }

    private String eventStreamNonSignatureHeadersHash(Instant instant, SigningResources resources) {
        var headers = Map.of(":date", HeaderValue.fromTimestamp(instant));
        var encodedHeaders = Message.encodeHeaders(headers.entrySet());
        return HexFormat.of().formatHex(hash(encodedHeaders, resources));
    }

    private byte[] deriveSigningKey(
            String secretKey,
            String dateStamp,
            String region,
            String service,
            SigningResources resources
    ) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret, resources);
        var kRegion = sign(region, kDate, resources);
        var kService = sign(service, kRegion, resources);
        return sign(TERMINATOR, kService, resources);
    }

    private byte[] sign(String data, byte[] key, SigningResources resources) {
        try {
            var mac = resources.sha256Mac;
            mac.reset();
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid signing key", e);
        }
    }

    private byte[] hash(byte[] data, SigningResources resources) {
        var digest = resources.sha256Digest;
        digest.reset();
        digest.update(data);
        return digest.digest();
    }

    private Message buildSignedMessage(
            byte[] originalMessage,
            byte[] binarySignature,
            Instant instant
    ) {
        // using a linked hash map to preserve order, the :chunk-signature should always be the last header
        var headers = new LinkedHashMap<String, HeaderValue>();
        headers.put(":date", HeaderValue.fromTimestamp(instant));
        headers.put(":chunk-signature", HeaderValue.fromByteArray(binarySignature));
        return new Message(headers, originalMessage);
    }
}
