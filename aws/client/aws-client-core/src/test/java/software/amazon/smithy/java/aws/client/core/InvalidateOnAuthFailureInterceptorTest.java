/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

class InvalidateOnAuthFailureInterceptorTest {

    @Test
    void invalidatesOnExpiredToken() {
        var counter = new CountingResolver();
        var interceptor = new InvalidateOnAuthFailureInterceptor(counter);

        interceptor.readAfterAttempt(null, authError("ExpiredToken"));
        assertEquals(1, counter.invalidateCount.get());
    }

    @Test
    void invalidatesOnAuthFailure() {
        var counter = new CountingResolver();
        var interceptor = new InvalidateOnAuthFailureInterceptor(counter);

        interceptor.readAfterAttempt(null, authError("AuthFailure"));
        assertEquals(1, counter.invalidateCount.get());
    }

    @Test
    void doesNotInvalidateOnNonAuthError() {
        var counter = new CountingResolver();
        var interceptor = new InvalidateOnAuthFailureInterceptor(counter);

        interceptor.readAfterAttempt(null, new RuntimeException("network error"));
        assertEquals(0, counter.invalidateCount.get());
    }

    @Test
    void doesNotInvalidateOnNull() {
        var counter = new CountingResolver();
        var interceptor = new InvalidateOnAuthFailureInterceptor(counter);

        interceptor.readAfterAttempt(null, null);
        assertEquals(0, counter.invalidateCount.get());
    }

    private static RuntimeException authError(String errorName) {
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

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
            return IdentityResult.of(AwsCredentialsIdentity.create("AK", "SK"));
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public void invalidate() {
            invalidateCount.incrementAndGet();
        }
    }
}
