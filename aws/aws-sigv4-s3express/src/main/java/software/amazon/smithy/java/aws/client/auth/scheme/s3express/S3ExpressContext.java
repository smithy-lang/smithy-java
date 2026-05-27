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

    /**
     * Callback used by the {@link S3ExpressIdentityProvider} to obtain bucket-scoped session
     * credentials by calling {@code S3:CreateSession}. Users register this on their client
     * builder so the {@link S3ExpressPlugin} can wire the auth scheme. The callback typically
     * delegates to the same generated S3 client the user is configuring (the call uses sigv4
     * because the rules engine routes {@code CreateSession} to a non-Express auth scheme).
     */
    public static final Context.Key<CreateSessionCallback> CREATE_SESSION_CALLBACK =
            Context.key("S3 Express CreateSession callback");

    private S3ExpressContext() {}
}
