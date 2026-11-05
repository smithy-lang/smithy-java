/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

final class SigV4aTestSupport {

    /** Credentials used throughout the aws-c-auth signing test suite. */
    static final String TEST_ACCESS_KEY_ID = "AKIDEXAMPLE";
    static final String TEST_SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    /** The P-256 public key aws-c-auth publishes for the credentials above (tests/aws-signing-test-suite/v4a). */
    static final String TEST_PUBLIC_X = "b6618f6a65740a99e650b33b6b4b5bd0d43b176d721a3edfea7e7d2d56d936b1";
    static final String TEST_PUBLIC_Y = "865ed22a7eadc9c5cb9d2cbaca1b3699139fedc5043dc6661864218330c8e518";

    private static final Map<String, ECPublicKey> PUBLIC_KEYS = new ConcurrentHashMap<>();

    private SigV4aTestSupport() {}

    static AwsCredentialsIdentity testIdentity() {
        return AwsCredentialsIdentity.create(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY);
    }

    static ECPublicKey testPublicKey() {
        return P256TestMath.publicKey(TEST_PUBLIC_X, TEST_PUBLIC_Y);
    }

    static ECPublicKey publicKeyFor(AwsCredentialsIdentity identity) {
        return PUBLIC_KEYS.computeIfAbsent(identity.accessKeyId() + '\u0000' + identity.secretAccessKey(), k -> {
            var resources = new SigningResources();
            var privateKey = SigV4aKeyDerivation.computePrivateKey(
                    identity.accessKeyId(),
                    identity.secretAccessKey(),
                    resources);
            return P256TestMath.publicKey(privateKey);
        });
    }

    static String signatureOf(String authorization) {
        int index = authorization.indexOf("Signature=");
        if (index < 0) {
            throw new AssertionError("No Signature= in " + authorization);
        }
        return authorization.substring(index + "Signature=".length());
    }

    static String withoutSignature(String authorization) {
        int index = authorization.indexOf("Signature=");
        return index < 0 ? authorization : authorization.substring(0, index + "Signature=".length());
    }

    static boolean verifies(ECPublicKey key, String stringToSign, String hexSignature) {
        return P256TestMath.verifyHex(key, stringToSign.getBytes(StandardCharsets.UTF_8), hexSignature);
    }
}
