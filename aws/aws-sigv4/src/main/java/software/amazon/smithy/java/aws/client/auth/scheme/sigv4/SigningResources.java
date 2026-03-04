/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;

/**
 * Precomputed resources needed for signing that can be pooled for resource reuse.
 */
final class SigningResources {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int BUFFER_SIZE = 512;

    private static final int POOL_SIZE = 32;

    static final Pool<SigningResources> RESOURCES_POOL = new Pool<>(POOL_SIZE, SigningResources::new);

    final StringBuilder sb;
    final MessageDigest sha256Digest;
    final Mac sha256Mac;

    SigningResources() {
        this.sb = new StringBuilder(BUFFER_SIZE);
        try {
            this.sha256Digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to fetch message digest instance for SHA-256", e);
        }
        try {
            this.sha256Mac = Mac.getInstance(HMAC_SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to fetch Mac instance for HmacSHA256", e);
        }
    }

    /**
     * Shrink the string builder to a max size of 512 to keep it always at or below this
     * when returned to the pool.
     */
    void shrink() {
        if (sb.length() > BUFFER_SIZE) {
            sb.setLength(BUFFER_SIZE);
            sb.trimToSize();
        }
        sb.setLength(0);
    }

    void reset() {
        sb.setLength(0);
        sha256Digest.reset();
        sha256Mac.reset();
    }

    /**
     * Returns a signing resource instance from the internal pool if available or a creates
     * a new instance if there's none available in the pool.
     *
     * @return a singing resources instance
     */
    static SigningResources get() {
        return RESOURCES_POOL.get();
    }

    /**
     * Returns the signing resource to the pool.
     *
     * @param signingResources the signing resource to release to the internal pool.
     */
    static void release(SigningResources signingResources) {
        signingResources.shrink();
        RESOURCES_POOL.release(signingResources);
    }
}
