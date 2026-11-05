/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Uses the AWS Common Runtime's NIST SP 800-108 counter-mode KDF ({@code aws-c-auth/key_derivation.c}).
 */
final class SigV4aKeyDerivation {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4aKeyDerivation.class);
    private static final byte[] KEY_PREFIX = "AWS4A".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LABEL = "AWS4-ECDSA-P256-SHA256".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_COUNTER = 254;
    private static final int KEY_LENGTH = 32;

    /** The order of the P-256 base point minus two, in big-endian form. */
    private static final byte[] N_MINUS_2 = {
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            0x00,
            0x00,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xBC,
            (byte) 0xE6,
            (byte) 0xFA,
            (byte) 0xAD,
            (byte) 0xA7,
            0x17,
            (byte) 0x9E,
            (byte) 0x84,
            (byte) 0xF3,
            (byte) 0xB9,
            (byte) 0xCA,
            (byte) 0xC2,
            (byte) 0xFC,
            0x63,
            0x25,
            0x4F};

    private static final ECParameterSpec P256 = loadP256();
    private static final SigningCache<CredentialsKey, ECPrivateKey> CACHE = new SigningCache<>(300);

    private SigV4aKeyDerivation() {}

    static ECParameterSpec p256() {
        return P256;
    }

    static ECPrivateKey derive(String accessKeyId, String secretAccessKey, SigningResources r) {
        ECPrivateKey memoized = r.memoizedEcdsaKey(accessKeyId, secretAccessKey);
        if (memoized != null) {
            return memoized;
        }
        var cacheKey = new CredentialsKey(accessKeyId, secretAccessKey);
        ECPrivateKey key = CACHE.get(cacheKey);
        if (key == null) {
            LOGGER.trace("Deriving SigV4a signing key: not found in cache.");
            key = computePrivateKey(accessKeyId, secretAccessKey, r);
            CACHE.put(cacheKey, key);
        }
        r.memoizeEcdsaKey(accessKeyId, secretAccessKey, key);
        return key;
    }

    static ECPrivateKey computePrivateKey(String accessKeyId, String secretAccessKey, SigningResources r) {
        byte[] secret = secretAccessKey.getBytes(StandardCharsets.UTF_8);
        byte[] inputKey = new byte[KEY_PREFIX.length + secret.length];
        System.arraycopy(KEY_PREFIX, 0, inputKey, 0, KEY_PREFIX.length);
        System.arraycopy(secret, 0, inputKey, KEY_PREFIX.length, secret.length);

        byte[] akid = accessKeyId.getBytes(StandardCharsets.UTF_8);
        byte[] fixedInput = new byte[4 + LABEL.length + 1 + akid.length + 1 + 4];
        int pos = 0;
        fixedInput[pos + 3] = 0x01; // 0x00000001, big-endian
        pos += 4;
        System.arraycopy(LABEL, 0, fixedInput, pos, LABEL.length);
        pos += LABEL.length;
        fixedInput[pos++] = 0x00;
        System.arraycopy(akid, 0, fixedInput, pos, akid.length);
        pos += akid.length;
        int counterIndex = pos++;
        fixedInput[pos + 2] = 0x01; // 0x00000100 = 256 bits of output, big-endian

        byte[] k0 = new byte[KEY_LENGTH];
        for (int counter = 1; counter <= MAX_COUNTER; counter++) {
            fixedInput[counterIndex] = (byte) counter;
            r.hmac(inputKey, fixedInput, 0, fixedInput.length, k0);
            BigInteger d = privateScalar(k0);
            if (d != null) {
                return toPrivateKey(d);
            }
        }
        throw new IllegalStateException("Unable to derive a SigV4a signing key from the given credentials");
    }

    /**
     * The range check and increment must process every byte without branches that depend on secret data.
     * JCA key import is not constant time.
     */
    static BigInteger privateScalar(byte[] k0) {
        if (exceedsNMinus2(k0)) {
            return null;
        }
        return new BigInteger(1, addOne(k0));
    }

    /**
     * {@code gt} records a greater byte only while all preceding bytes are equal.
     * {@code eq} remains 1 while the preceding bytes are equal.
     */
    static boolean exceedsNMinus2(byte[] k0) {
        int gt = 0;
        int eq = 1;
        for (int i = 0; i < KEY_LENGTH; i++) {
            int l = k0[i] & 0xFF;
            int r = N_MINUS_2[i] & 0xFF;
            gt |= ((r - l) >>> 31) & eq;
            eq &= ((l ^ r) - 1) >>> 31;
        }
        return gt == 1;
    }

    /** Propagate the carry through every byte to keep the increment constant time. */
    static byte[] addOne(byte[] k0) {
        byte[] out = new byte[KEY_LENGTH];
        int carry = 1;
        for (int i = KEY_LENGTH - 1; i >= 0; i--) {
            int v = (k0[i] & 0xFF) + carry;
            out[i] = (byte) v;
            carry = v >>> 8;
        }
        return out;
    }

    private static ECPrivateKey toPrivateKey(BigInteger d) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, P256));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Unable to create a P-256 private key for SigV4a signing", e);
        }
    }

    private static ECParameterSpec loadP256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JCA providers do not supply the secp256r1 curve required by SigV4a",
                    e);
        }
    }

    /** Exclude the secret from {@code toString} to prevent disclosure in logs. */
    private record CredentialsKey(String accessKeyId, String secretAccessKey) {
        @Override
        public String toString() {
            return "CredentialsKey[accessKeyId=" + accessKeyId + "]";
        }
    }
}
