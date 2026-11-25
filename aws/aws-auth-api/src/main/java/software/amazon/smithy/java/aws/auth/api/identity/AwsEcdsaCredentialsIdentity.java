/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.auth.api.identity;

import java.security.interfaces.ECPrivateKey;
import java.time.Instant;

/**
 * AWS credentials that sign SigV4a requests with an ECDSA P-256 private key: AWS access key ID, ECDSA private key,
 * optional session token, and optional AWS account ID.
 *
 * <p>SigV4a normally derives its ECDSA key from the secret access key. Some credentials, such as asymmetric
 * session credentials, are issued with an ECDSA key pair and have no secret access key. The SigV4a signer uses
 * {@link #ecdsaPrivateKey()} as the signing key for these credentials instead of deriving one.
 *
 * <p>These credentials have no secret access key, so they cannot sign SigV4 requests.
 * {@link #secretAccessKey()} throws {@link UnsupportedOperationException}.
 */
public interface AwsEcdsaCredentialsIdentity extends AwsCredentialsIdentity {
    /**
     * Retrieve the ECDSA P-256 private key used to compute SigV4a signatures.
     *
     * @return the private key.
     */
    ECPrivateKey ecdsaPrivateKey();

    /**
     * Constructs a new credentials object with the specified AWS access key, ECDSA private key, and session token.
     *
     * @param accessKeyId The AWS access key, used to identify the user interacting with services.
     * @param ecdsaPrivateKey The ECDSA P-256 private key, used to compute SigV4a signatures.
     * @param sessionToken The AWS session token, used for authenticating temporary access some resource.
     * @return the created identity.
     * @throws IllegalArgumentException if the private key is not a P-256 key.
     */
    static AwsEcdsaCredentialsIdentity create(String accessKeyId, ECPrivateKey ecdsaPrivateKey, String sessionToken) {
        return create(accessKeyId, ecdsaPrivateKey, sessionToken, null, null);
    }

    /**
     * Constructs a new credentials object with the specified AWS access key, ECDSA private key, session token,
     * expiration time, and account ID.
     *
     * @param accessKeyId The AWS access key, used to identify the user interacting with services.
     * @param ecdsaPrivateKey The ECDSA P-256 private key, used to compute SigV4a signatures.
     * @param sessionToken The AWS session token, used for authenticating temporary access some resource.
     * @param expirationTime When the credentials expire, or null if no expiration or unknown.
     * @param accountId The AWS account ID associated with the credentials, or null if no account ID or unknown.
     * @return the created identity.
     * @throws IllegalArgumentException if the private key is not a P-256 key.
     */
    static AwsEcdsaCredentialsIdentity create(
            String accessKeyId,
            ECPrivateKey ecdsaPrivateKey,
            String sessionToken,
            Instant expirationTime,
            String accountId
    ) {
        return new AwsEcdsaCredentialsIdentityRecord(
                accessKeyId,
                ecdsaPrivateKey,
                sessionToken,
                expirationTime,
                accountId);
    }
}
