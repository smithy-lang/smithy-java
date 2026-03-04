/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.events.AwsEventFrame;
import software.amazon.smithy.java.context.Context;

class SigV4EventSignerTest {

    private static final AwsCredentialsIdentity TEST_CREDENTIALS = AwsCredentialsIdentity
            .create("fake access key", "fake secret key");

    private static final List<Instant> SIGNING_INSTANTS = List.of(
            // Note: This first Instant is used for signing the request not an event
            OffsetDateTime.of(2026, 1, 16, 6, 30, 0, 0, ZoneOffset.UTC).toInstant(),
            OffsetDateTime.of(2026, 1, 16, 6, 30, 1, 0, ZoneOffset.UTC).toInstant(),
            OffsetDateTime.of(2026, 1, 16, 6, 30, 2, 0, ZoneOffset.UTC).toInstant(),
            OffsetDateTime.of(2026, 1, 16, 6, 30, 3, 0, ZoneOffset.UTC).toInstant());

    @Test
    void testSignPayload() throws Exception {
        var headers = new HashMap<String, HeaderValue>();
        headers.put("some-header", HeaderValue.fromString("value"));
        var messageToSign = new Message(headers, "test payload".getBytes());

        var epoch = Instant.ofEpochSecond(123_456_789L);
        var testClock = Clock.fixed(epoch, ZoneOffset.UTC);

        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "testservice")
                .put(SigV4Settings.CLOCK, testClock);

        // Previous signature is hex string
        var prevSignature = hash("last message sts".getBytes());

        var signer = new SigV4EventSigner(TEST_CREDENTIALS, context, prevSignature);
        var result = signer.transformFrame(new AwsEventFrame(messageToSign));

        assertNotNull(result);
        var signedMessage = result.unwrap();

        var dateHeader = signedMessage.getHeaders().get(":date");
        assertNotNull(dateHeader);
        assertEquals(epoch, dateHeader.getTimestamp());

        var sigHeader = signedMessage.getHeaders().get(":chunk-signature");
        assertNotNull(sigHeader);
        var actualSignature = HexFormat.of().formatHex(sigHeader.getByteArray());

        // Verify signature is valid hex and correct length
        assertNotNull(actualSignature);
        assertEquals(64, actualSignature.length());
        assertEquals("1ea04a4f6becd85ae3e38e379ffaf4bb95042603f209512476cc6416868b31ee", actualSignature);
    }

    @Test
    void testEventStreamSigning() throws Exception {
        var credentials = AwsCredentialsIdentity.create("access", "secret");

        var eventClock = eventSigningClock();

        var context = Context.create()
                .put(SigV4Settings.REGION, "us-west-2")
                .put(SigV4Settings.SIGNING_NAME, "demo")
                .put(SigV4Settings.CLOCK, eventClock);

        var seedSignature = "e1d8e8c8815e60969f2a34765c9a15945ffc0badbaa4b7e3b163ea19131e949b";
        var signer = new SigV4EventSigner(credentials, context, seedSignature);

        // Expected signatures from AWS SDK v2 test
        var expectedSignatures = List.of(
                "4d6dc4197ca9045f693131a8321bffd3f0b5308835a2770c980551d2fd4b2e56",
                "75342279dd941fef5a494cc4b234545ee95be35805d5489ffb24959a9601d6ed",
                "c83a5b9f19bc3fe8247eec6d4a5d7806f4b47a6457559c58055c7017c6f3ce3e",
                "8b01a16c997aa5692d552b0b6cc4f308743882a0a035d9841ba0738a53420bef");

        // Sign each payload
        var payloads = List.of("A", "B", "C", "");

        for (var idx = 0; idx < payloads.size(); idx++) {
            var payload = payloads.get(idx).getBytes(StandardCharsets.UTF_8);
            var message = new Message(new HashMap<>(), payload);
            var signedFrame = signer.transformFrame(new AwsEventFrame(message));

            assertNotNull(signedFrame);
            var signedMessage = signedFrame.unwrap();

            var sigHeader = signedMessage.getHeaders().get(":chunk-signature");
            assertNotNull(sigHeader);
            var actualSignature = HexFormat.of().formatHex(sigHeader.getByteArray());

            assertEquals(expectedSignatures.get(idx),
                    actualSignature,
                    "Signature mismatch for payload " + idx + ": " + payloads.get(idx));
        }
    }

    @Test
    void testSignatureChaining() throws Exception {
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-2")
                .put(SigV4Settings.SIGNING_NAME, "test");

        var initialSignature = hash(new byte[0]);
        var signer = new SigV4EventSigner(TEST_CREDENTIALS, context, initialSignature);

        var message1 = new Message(new HashMap<>(), "first".getBytes());
        var signed1 = signer.transformFrame(new AwsEventFrame(message1));
        assertNotNull(signed1);

        var sig1 = signed1.unwrap().getHeaders().get(":chunk-signature").getByteArray();

        var message2 = new Message(new HashMap<>(), "second".getBytes());
        var signed2 = signer.transformFrame(new AwsEventFrame(message2));
        assertNotNull(signed2);

        var sig2 = signed2.unwrap().getHeaders().get(":chunk-signature").getByteArray();

        // Signatures should be different due to chaining
        var sig1Hex = HexFormat.of().formatHex(sig1);
        var sig2Hex = HexFormat.of().formatHex(sig2);
        assertNotNull(sig1Hex);
        assertNotNull(sig2Hex);
        assertEquals(64, sig1Hex.length());
        assertEquals(64, sig2Hex.length());
    }

    private static String hash(byte[] data) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }

    private static Clock eventSigningClock() {
        return new Clock() {
            private int idx = 0;

            @Override
            public Instant instant() {
                if (idx >= SIGNING_INSTANTS.size()) {
                    throw new IllegalStateException("Clock ran out of Instants to return! " + idx);
                }
                return SIGNING_INSTANTS.get(idx++);
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
