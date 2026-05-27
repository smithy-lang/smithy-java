/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.context.Context;

/**
 * Context keys used by the S3 Express auth scheme to communicate per-call data.
 */
public final class S3ExpressContext {

    /**
     * Bucket name on the in-flight request. Must be set by an interceptor that pulls the value
     * from the input shape before the auth pipeline asks for an identity. The
     * {@link S3ExpressIdentityProvider} reads this key when resolving session credentials.
     */
    public static final Context.Key<String> BUCKET = Context.key("S3 Express target bucket");

    private S3ExpressContext() {}
}
