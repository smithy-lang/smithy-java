/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.auth.api.identity;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.time.Instant;
import java.util.Objects;

/**
 * Implementation of {@link AwsEcdsaCredentialsIdentity}.
 */
record AwsEcdsaCredentialsIdentityRecord(
        String accessKeyId,
        ECPrivateKey ecdsaPrivateKey,
        String sessionToken,
        Instant expirationTime,
        String accountId) implements AwsEcdsaCredentialsIdentity {

    private static final EllipticCurve P256 = loadP256Curve();

    AwsEcdsaCredentialsIdentityRecord {
        Objects.requireNonNull(accessKeyId, "accessKeyId is null");
        Objects.requireNonNull(ecdsaPrivateKey, "ecdsaPrivateKey is null");
        var params = ecdsaPrivateKey.getParams();
        if (params == null || !P256.equals(params.getCurve())) {
            throw new IllegalArgumentException("ecdsaPrivateKey must be a NIST P-256 (secp256r1) key");
        }
    }

    @Override
    public String secretAccessKey() {
        throw new UnsupportedOperationException(
                "ECDSA credentials have no secret access key and can only be used for SigV4a signing");
    }

    /** Exclude the private key and session token from {@code toString} to prevent disclosure in logs. */
    @Override
    public String toString() {
        return "AwsEcdsaCredentialsIdentity[accessKeyId=" + accessKeyId
                + ", ecdsaPrivateKey=***"
                + ", sessionToken=" + (sessionToken == null ? "null" : "***")
                + ", expirationTime=" + expirationTime
                + ", accountId=" + accountId + "]";
    }

    private static EllipticCurve loadP256Curve() {
        try {
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class).getCurve();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JCA providers do not supply the secp256r1 curve", e);
        }
    }
}
