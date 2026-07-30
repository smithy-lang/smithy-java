/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.model.shapes.ShapeId;

class InvalidateCredentialsInterceptorTest {

    private static final AwsCredentialsIdentity IDENTITY = AwsCredentialsIdentity.create("AK", "SK");

    @Test
    void invalidatesOnExpiredToken() {
        var counter = new CountingResolver();
        var interceptor = InvalidateCredentialsInterceptor.INSTANCE;

        interceptor.invalidate(context(counter, IDENTITY), credentialError("ExpiredToken"));
        assertEquals(1, counter.invalidateCount.get());
        assertSame(IDENTITY, counter.invalidatedIdentity);
    }

    @Test
    void invalidatesOnInvalidToken() {
        var counter = new CountingResolver();
        var interceptor = InvalidateCredentialsInterceptor.INSTANCE;

        interceptor.invalidate(context(counter, IDENTITY), credentialError("InvalidToken"));
        assertEquals(1, counter.invalidateCount.get());
    }

    @Test
    void invalidatesUnmodeledResponseErrorCode() {
        var counter = new CountingResolver();
        var context = context(counter, IDENTITY);
        context.put(CallContext.RESPONSE_ERROR_CODE, "ExpiredToken");

        InvalidateCredentialsInterceptor.INSTANCE.invalidate(context, new CallException("unmodeled error"));

        assertEquals(1, counter.invalidateCount.get());
        assertSame(IDENTITY, counter.invalidatedIdentity);
    }

    @Test
    void doesNotInvalidateOnOtherModeledError() {
        var counter = new CountingResolver();
        var interceptor = InvalidateCredentialsInterceptor.INSTANCE;

        interceptor.invalidate(context(counter, IDENTITY), credentialError("AccessDenied"));
        assertEquals(0, counter.invalidateCount.get());
    }

    @Test
    void doesNotInvalidateOnNonModeledError() {
        var counter = new CountingResolver();
        var interceptor = InvalidateCredentialsInterceptor.INSTANCE;

        interceptor.invalidate(context(counter, IDENTITY), new RuntimeException("network error"));
        assertEquals(0, counter.invalidateCount.get());
    }

    @Test
    void doesNotInvalidateOnNull() {
        var counter = new CountingResolver();
        var interceptor = InvalidateCredentialsInterceptor.INSTANCE;

        interceptor.invalidate(context(counter, IDENTITY), null);
        assertEquals(0, counter.invalidateCount.get());
    }

    @Test
    void doesNotInvalidateWithoutAttemptContext() {
        InvalidateCredentialsInterceptor.INSTANCE.invalidate(Context.empty(), credentialError("ExpiredToken"));
    }

    @Test
    void credentialErrorsAreMarkedNonRetryableWithoutAttemptContext() {
        var context = Context.create();
        context.put(CallContext.RESPONSE_ERROR_CODE, "ExpiredToken");
        var error = new CallException("expired credentials");
        error.isRetrySafe(RetrySafety.YES);

        InvalidateCredentialsInterceptor.INSTANCE.invalidate(context, error);

        assertEquals(RetrySafety.NO, error.isRetrySafe());
    }

    @Test
    void doesNotInvalidateWhenIdentityTypeDoesNotMatch() {
        var counter = new CountingResolver();
        var context = Context.create();
        context.put(CallContext.IDENTITY, new Identity() {});
        context.put(CallContext.IDENTITY_RESOLVER, counter);

        InvalidateCredentialsInterceptor.INSTANCE.invalidate(context, credentialError("ExpiredToken"));

        assertEquals(0, counter.invalidateCount.get());
    }

    @Test
    void pluginInstallsInterceptorForCustomResolver() {
        var resolver = new CountingResolver();
        var scheme = AuthScheme.of(
                ShapeId.from("aws.auth#sigv4"),
                Object.class,
                AwsCredentialsIdentity.class,
                (request, identity, properties) -> new SignResult<>(request));
        var config = ClientConfig.builder()
                .putSupportedAuthSchemes(scheme)
                .addIdentityResolver(resolver);

        new AwsCredentialChainPlugin().configureClient(config);

        assertEquals(1, config.identityResolvers().size());
        assertSame(resolver, config.identityResolvers().get(0));
        assertTrue(config.interceptors().contains(InvalidateCredentialsInterceptor.INSTANCE));
    }

    private static Context context(IdentityResolver<?> resolver, AwsCredentialsIdentity identity) {
        var context = Context.create();
        context.put(CallContext.IDENTITY, identity);
        context.put(CallContext.IDENTITY_RESOLVER, resolver);
        return context;
    }

    private static RuntimeException credentialError(String errorName) {
        Schema schema = Schema.createString(ShapeId.from("com.example#" + errorName));
        return new ModeledException(schema, errorName + " error") {
            @Override
            public void serialize(ShapeSerializer serializer) {}

            @Override
            public void serializeMembers(ShapeSerializer serializer) {}

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
    }

    private static class CountingResolver implements IdentityResolver<AwsCredentialsIdentity> {
        final AtomicInteger invalidateCount = new AtomicInteger(0);
        AwsCredentialsIdentity invalidatedIdentity;

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
            return IdentityResult.of(IDENTITY);
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
            invalidatedIdentity = rejectedIdentity;
            invalidateCount.incrementAndGet();
        }
    }
}
