/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.util.Objects;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * Cache key for the {@link S3ExpressIdentityCache}: the bucket plus the credentials used to
 * authorize {@code CreateSession} on that bucket.
 *
 * <p>Equality covers the bucket plus the access key / secret key / session token of the base
 * credentials. {@code expirationTime} and {@code accountId} are intentionally excluded so that
 * a credential rotation that produces the same key material can reuse the cached session
 * credential. {@code sessionToken} is included to defend against confused-deputy across
 * different STS sessions sharing an access key (rare, but possible).
 *
 * <p>The hash is precomputed in the constructor since the same key is hashed repeatedly on the
 * cache hot path.
 */
final class S3ExpressIdentityKey {

    private final String bucket;
    private final AwsCredentialsIdentity identity;
    private final int hash;

    S3ExpressIdentityKey(String bucket, AwsCredentialsIdentity identity) {
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.hash = Objects.hash(
                bucket,
                identity.accessKeyId(),
                identity.secretAccessKey(),
                identity.sessionToken());
    }

    String bucket() {
        return bucket;
    }

    AwsCredentialsIdentity identity() {
        return identity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof S3ExpressIdentityKey other)) {
            return false;
        }
        return bucket.equals(other.bucket)
                && identity.accessKeyId().equals(other.identity.accessKeyId())
                && identity.secretAccessKey().equals(other.identity.secretAccessKey())
                && Objects.equals(identity.sessionToken(), other.identity.sessionToken());
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
