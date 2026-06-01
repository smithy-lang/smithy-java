/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;

/**
 * Reads the {@code Bucket} member off any S3 operation input and publishes it to
 * {@link S3ExpressContext#BUCKET} on the call context, so the
 * {@link S3ExpressIdentityProvider} can resolve session credentials for the right bucket.
 *
 * <p>Schema-driven: the interceptor looks for a member named {@code Bucket} on the operation's
 * input schema. Operations without a bucket member (e.g. {@code ListBuckets}) are ignored — for
 * those calls the rules engine resolves to a non-Express endpoint and S3 Express auth never
 * runs.
 *
 * <p>Runs in {@code readBeforeExecution}, before any auth-resolution stage.
 */
final class S3ExpressBucketInterceptor implements ClientInterceptor {

    static final S3ExpressBucketInterceptor INSTANCE = new S3ExpressBucketInterceptor();

    private S3ExpressBucketInterceptor() {}

    @Override
    public void readBeforeExecution(InputHook<?, ?> hook) {
        var inputSchema = hook.operation().inputSchema();
        var bucketMember = inputSchema.member("Bucket");
        if (bucketMember != null) {
            Object bucket = hook.input().getMemberValue(bucketMember);
            if (bucket instanceof String s && !s.isEmpty()) {
                hook.context().put(S3ExpressContext.BUCKET, s);
            }
        }
    }
}
