/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.io.ByteBufferUtils;

class SigV4aEventSignerTest {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final List<Instant> INSTANTS = List.of(
            Instant.parse("2026-01-16T06:30:01Z"),
            Instant.parse("2026-01-16T06:30:02Z"),
            Instant.parse("2026-01-17T00:00:00Z"));

    @Test
    void signsAndChainsFramesWithVerifiableEcdsaSignatures() throws Exception {
        var identity = SigV4aTestSupport.testIdentity();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-west-2")
                .put(SigV4Settings.SIGNING_NAME, "demo")
                .put(SigV4Settings.CLOCK, tickingClock());
        var seed = "3044022051fe398025aafbfc21d054bc78e5edfb96c9acb7fd272795565181d757815e47"
                + "02202e7b8d2b92324290b1d95f8b0fc5e333bb8b5e333f6160bcab39d7258156d224";
        var signer = new SigV4aEventSigner(identity, context, seed + "****");
        var publicKey = SigV4aTestSupport.testPublicKey();

        var first = new Message(Map.of("some-header", HeaderValue.fromString("value")), "A".getBytes());
        var signedFirst = signer.transformFrame(new AwsEventFrame(first)).unwrap();
        assertThat(signedFirst.getHeaders().keySet()).containsExactly(":date", ":chunk-signature");
        assertThat(signedFirst.getHeaders().get(":date").getTimestamp()).isEqualTo(INSTANTS.get(0));
        assertThat(signedFirst.getPayload()).isEqualTo(ByteBufferUtils.getBytes(first.toByteBuffer()));
        byte[] firstSignature = signedFirst.getHeaders().get(":chunk-signature").getByteArray();
        var firstStringToSign = stringToSign(INSTANTS.get(0), "demo", seed, signedFirst.getPayload());
        assertThat(P256TestMath.verify(publicKey, utf8(firstStringToSign), firstSignature)).isTrue();

        var second = new Message(new HashMap<>(), "B".getBytes());
        var signedSecond = signer.transformFrame(new AwsEventFrame(second)).unwrap();
        byte[] secondSignature = signedSecond.getHeaders().get(":chunk-signature").getByteArray();
        var secondStringToSign = stringToSign(
                INSTANTS.get(1),
                "demo",
                HexFormat.of().formatHex(firstSignature),
                signedSecond.getPayload());
        assertThat(P256TestMath.verify(publicKey, utf8(secondStringToSign), secondSignature)).isTrue();
        assertThat(P256TestMath.verify(publicKey, utf8(firstStringToSign), secondSignature)).isFalse();

        var closing = signer.closingFrame().unwrap();
        assertThat(closing.getPayload()).isEmpty();
        byte[] closingSignature = closing.getHeaders().get(":chunk-signature").getByteArray();
        var closingStringToSign = stringToSign(
                INSTANTS.get(2),
                "demo",
                HexFormat.of().formatHex(secondSignature),
                new byte[0]);
        assertThat(closingStringToSign).contains("\n20260117/demo/aws4_request\n");
        assertThat(P256TestMath.verify(publicKey, utf8(closingStringToSign), closingSignature)).isTrue();
    }

    @Test
    void usesTheEndpointSigningNameOverrideLikeTheRequestSigner() throws Exception {
        var identity = SigV4aTestSupport.testIdentity();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-west-2")
                .put(SigV4Settings.SIGNING_NAME, "service")
                .put(EndpointAuthSchemeSettings.SIGNING_NAME, "endpoint-service")
                .put(SigV4Settings.CLOCK, tickingClock());
        var signer = new SigV4aEventSigner(identity, context, "abc123");

        var signed = signer.transformFrame(new AwsEventFrame(new Message(new HashMap<>(), "A".getBytes()))).unwrap();
        byte[] signature = signed.getHeaders().get(":chunk-signature").getByteArray();
        var publicKey = SigV4aTestSupport.testPublicKey();

        var endpointScope = stringToSign(INSTANTS.get(0), "endpoint-service", "abc123", signed.getPayload());
        var schemeScope = stringToSign(INSTANTS.get(0), "service", "abc123", signed.getPayload());
        assertThat(P256TestMath.verify(publicKey, utf8(endpointScope), signature)).isTrue();
        assertThat(P256TestMath.verify(publicKey, utf8(schemeScope), signature)).isFalse();
    }

    @Test
    void rejectsANullSeedSignature() {
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-west-2")
                .put(SigV4Settings.SIGNING_NAME, "demo");
        assertThatThrownBy(() -> new SigV4aEventSigner(SigV4aTestSupport.testIdentity(), context, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("seedSignature");
    }

    private static String stringToSign(Instant instant, String service, String previousSignature, byte[] payload)
            throws Exception {
        var dateTime = STAMP.format(instant);
        var scope = dateTime.substring(0, 8) + '/' + service + "/aws4_request";
        var dateHeader = Message.encodeHeaders(Map.of(":date", HeaderValue.fromTimestamp(instant)).entrySet());
        return "AWS4-ECDSA-P256-SHA256-PAYLOAD\n"
                + dateTime + '\n'
                + scope + '\n'
                + previousSignature + '\n'
                + sha256Hex(dateHeader) + '\n'
                + sha256Hex(payload);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Clock tickingClock() {
        return new Clock() {
            private int index;

            @Override
            public Instant instant() {
                return INSTANTS.get(index++);
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
