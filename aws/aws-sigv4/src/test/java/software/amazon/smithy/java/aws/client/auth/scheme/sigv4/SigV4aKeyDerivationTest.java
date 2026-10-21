/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

class SigV4aKeyDerivationTest {

    @Test
    void derivesTheCrtReferencePublicKey() {
        var resources = new SigningResources();
        var key = SigV4aKeyDerivation.computePrivateKey(
                SigV4aTestSupport.TEST_ACCESS_KEY_ID,
                SigV4aTestSupport.TEST_SECRET_ACCESS_KEY,
                resources);

        var point = P256TestMath.multiplyG(key.getS());

        assertThat(point.isOnCurve()).isTrue();
        assertThat(P256TestMath.coordinateHex(point.x())).isEqualTo(SigV4aTestSupport.TEST_PUBLIC_X);
        assertThat(P256TestMath.coordinateHex(point.y())).isEqualTo(SigV4aTestSupport.TEST_PUBLIC_Y);
    }

    @Test
    void testMathGeneratorHasOrderN() {
        assertThat(P256TestMath.multiplyG(P256TestMath.N).isInfinity()).isTrue();
        assertThat(P256TestMath.multiplyG(BigInteger.ONE)).isEqualTo(new P256TestMath.Point(P256TestMath.GX,
                P256TestMath.GY));
    }

    @Test
    void privateScalarIsKdfOutputPlusOne() {
        byte[] zero = new byte[32];
        assertThat(SigV4aKeyDerivation.privateScalar(zero)).isEqualTo(BigInteger.ONE);

        byte[] five = new byte[32];
        five[31] = 5;
        assertThat(SigV4aKeyDerivation.privateScalar(five)).isEqualTo(BigInteger.valueOf(6));
    }

    @Test
    void privateScalarRejectsCandidatesAboveNMinusTwo() {
        BigInteger nMinus2 = P256TestMath.N.subtract(BigInteger.TWO);
        assertThat(SigV4aKeyDerivation.privateScalar(toBytes(nMinus2)))
                .isEqualTo(P256TestMath.N.subtract(BigInteger.ONE));
        assertThat(SigV4aKeyDerivation.privateScalar(toBytes(P256TestMath.N.subtract(BigInteger.ONE)))).isNull();
        assertThat(SigV4aKeyDerivation.privateScalar(toBytes(P256TestMath.N))).isNull();

        byte[] max = new byte[32];
        Arrays.fill(max, (byte) 0xFF);
        assertThat(SigV4aKeyDerivation.privateScalar(max)).isNull();
    }

    @Test
    void derivedKeysAreCachedPerCredentials() {
        var resources = new SigningResources();
        var first = SigV4aKeyDerivation.derive("AKIDCACHE", "secret", resources);
        var second = SigV4aKeyDerivation.derive("AKIDCACHE", "secret", resources);
        var fresh = SigV4aKeyDerivation.derive("AKIDCACHE", "secret", new SigningResources());
        var other = SigV4aKeyDerivation.derive("AKIDOTHER", "secret", resources);

        assertThat(second).isSameAs(first);
        assertThat(fresh).isSameAs(first);
        assertThat(other.getS()).isNotEqualTo(first.getS());
        assertThat(SigV4aKeyDerivation.derive("AKIDCACHE", "secret2", resources).getS()).isNotEqualTo(first.getS());
    }

    @Test
    void signaturesVerifyUnderIndependentlyComputedPublicKeys() {
        var random = new Random(7);
        var resources = new SigningResources();
        byte[] message = "string to sign".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 5; i++) {
            var identity = AwsCredentialsIdentity.create("AKID" + random.nextInt(1_000_000),
                    Long.toString(random.nextLong(), 36));
            var key = SigV4aKeyDerivation.derive(identity.accessKeyId(), identity.secretAccessKey(), resources);

            byte[] signature = resources.ecdsaSign(key, message, 0, message.length);

            var publicKey = SigV4aTestSupport.publicKeyFor(identity);
            assertThat(P256TestMath.verify(publicKey, message, signature)).as("identity %s", identity).isTrue();
            assertThat(P256TestMath.verify(publicKey, "other".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        }
    }

    @Test
    void constantTimeHelpersMatchBigIntegerArithmetic() {
        var random = new Random(11);
        BigInteger nMinus2 = P256TestMath.N.subtract(BigInteger.TWO);
        for (int i = 0; i < 2_000; i++) {
            byte[] k0 = new byte[32];
            random.nextBytes(k0);
            if (i % 4 == 0) {
                System.arraycopy(toBytes(nMinus2), 0, k0, 0, 16 + random.nextInt(16));
            }
            BigInteger value = new BigInteger(1, k0);
            assertThat(SigV4aKeyDerivation.exceedsNMinus2(k0)).isEqualTo(value.compareTo(nMinus2) > 0);
            assertThat(new BigInteger(1, SigV4aKeyDerivation.addOne(k0))).isEqualTo(value.add(BigInteger.ONE));
        }
        byte[] allOnes = new byte[32];
        Arrays.fill(allOnes, (byte) 0xFF);
        // Key derivation rejects this block before increment, so the wraparound cannot affect the key.
        assertThat(new BigInteger(1, SigV4aKeyDerivation.addOne(allOnes))).isEqualTo(BigInteger.ZERO);
    }

    private static byte[] toBytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }
}
