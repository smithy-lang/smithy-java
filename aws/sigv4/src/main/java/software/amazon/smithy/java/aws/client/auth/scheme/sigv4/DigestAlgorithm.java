/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

enum DigestAlgorithm {

    SHA1("SHA-1"),
    MD5("MD5"),
    SHA256("SHA-256");

    private final String algorithmName;
    private final DigestThreadLocal digestReference;

    DigestAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
        digestReference = new DigestThreadLocal(algorithmName);
    }

    @Override
    public String toString() {
        return algorithmName;
    }

    MessageDigest getDigest() {
        MessageDigest digest = digestReference.get();
        digest.reset();
        return digest;
    }

    private static class DigestThreadLocal extends ThreadLocal<MessageDigest> {
        private final String algorithmName;

        DigestThreadLocal(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance(algorithmName);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(
                    "Unable to fetch message digest instance for Algorithm "
                        + algorithmName + ": " + e.getMessage(),
                    e
                );
            }
        }
    }
}
