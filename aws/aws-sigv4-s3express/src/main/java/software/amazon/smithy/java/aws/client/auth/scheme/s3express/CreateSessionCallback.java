/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * User-supplied bridge that calls S3's {@code CreateSession} operation for a directory bucket.
 *
 * <p>This module doesn't depend on any generated S3 client; the caller wires this lambda to
 * their own client at construction time:
 *
 * <pre>{@code
 * (bucket, baseCreds) -> {
 *     var resp = s3Client.createSession(CreateSessionInput.builder().bucket(bucket).build());
 *     return S3ExpressIdentity.create(
 *         resp.credentials().accessKeyId(),
 *         resp.credentials().secretAccessKey(),
 *         resp.credentials().sessionToken(),
 *         resp.credentials().expiration());
 * }
 * }</pre>
 *
 * <p>Implementations are called on the S3 Express identity-cache executor and may be invoked
 * concurrently for different buckets but never concurrently for the same bucket.
 */
@FunctionalInterface
public interface CreateSessionCallback {
    S3ExpressIdentity createSession(String bucket, AwsCredentialsIdentity baseCredentials);
}
