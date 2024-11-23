/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;

enum SigningAlgorithm {

    HMAC_SHA256("HmacSHA256");

    private final String algorithmName;
    private final ThreadLocal<Mac> macReference;

    SigningAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
        macReference = new MacThreadLocal(algorithmName);
    }

    Mac getMac() {
        return macReference.get();
    }

    @Override
    public String toString() {
        return algorithmName;
    }

    private static class MacThreadLocal extends ThreadLocal<Mac> {
        private final String algorithmName;

        MacThreadLocal(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        @Override
        protected Mac initialValue() {
            try {
                return Mac.getInstance(algorithmName);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(
                    "Unable to fetch Mac instance for Algorithm "
                        + algorithmName + ": " + e.getMessage()
                );
            }
        }
    }
}
