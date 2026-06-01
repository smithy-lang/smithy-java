/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

class S3ExpressIdentityProviderTest {

    @Test
    void invalidateClearsCachedSessions() {
        var sessionCount = new AtomicInteger();
        var provider = new S3ExpressIdentityProvider(
                staticBaseIdentity(),
                (bucket, baseCredentials) -> AwsCredentialsIdentity.create(
                        "SESSION-" + sessionCount.incrementAndGet(),
                        "secret",
                        "token",
                        null));
        var context = contextWithBucket("bucket");

        var first = provider.resolveIdentity(context).identity();
        var cached = provider.resolveIdentity(context).identity();
        provider.invalidate();
        var refreshed = provider.resolveIdentity(context).identity();

        assertThat(cached, is(first));
        assertThat(refreshed.accessKeyId(), equalTo("SESSION-2"));
        assertThat(sessionCount.get(), equalTo(2));
    }

    @Test
    void preservesBaseIdentityError() {
        IdentityResolver<AwsCredentialsIdentity> failingBase = new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                return IdentityResult.ofError(getClass(), "missing base creds");
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
        var provider = new S3ExpressIdentityProvider(
                failingBase,
                (bucket, baseCredentials) -> {
                    throw new AssertionError("CreateSession should not be called");
                });

        var result = provider.resolveIdentity(contextWithBucket("bucket"));

        assertThat(result.resolver(), equalTo(failingBase.getClass()));
        assertThat(result.error(), containsString("missing base creds"));
    }

    private static IdentityResolver<AwsCredentialsIdentity> staticBaseIdentity() {
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(AwsCredentialsIdentity.create("AKID", "secret"));
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private static Context contextWithBucket(String bucket) {
        var context = Context.create();
        context.put(S3ExpressContext.BUCKET, bucket);
        return context;
    }
}
