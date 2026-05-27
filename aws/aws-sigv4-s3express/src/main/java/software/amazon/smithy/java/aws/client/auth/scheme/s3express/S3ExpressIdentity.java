/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.time.Instant;
import java.util.Objects;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * Bucket-scoped session credentials returned by S3 {@code CreateSession}.
 *
 * <p>An S3 Express identity is an {@link AwsCredentialsIdentity} (access key id, secret key,
 * session token) that is only valid for the directory bucket it was issued for. The session
 * token is sent on the wire as the {@code x-amz-s3session-token} header (not as
 * {@code x-amz-security-token}).
 */
public interface S3ExpressIdentity extends AwsCredentialsIdentity {

    /**
     * Build a new {@code S3ExpressIdentity}.
     *
     * @param accessKeyId     access key id from CreateSession.
     * @param secretAccessKey secret access key from CreateSession.
     * @param sessionToken    session token from CreateSession.
     * @param expirationTime  when the credentials expire, or null if unknown.
     */
    static S3ExpressIdentity create(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            Instant expirationTime
    ) {
        Objects.requireNonNull(accessKeyId, "accessKeyId");
        Objects.requireNonNull(secretAccessKey, "secretAccessKey");
        Objects.requireNonNull(sessionToken, "sessionToken");
        return new S3ExpressIdentityRecord(accessKeyId, secretAccessKey, sessionToken, expirationTime);
    }
}
