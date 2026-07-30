/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

class LeasedIdentityResolverTest {

    @Test
    void sharesDelegateUntilLastLeaseClosesAndRecreatesItLater() throws Exception {
        var creations = new AtomicInteger();
        var closes = new AtomicInteger();
        var current = new AtomicReference<TestResolver>();
        var resolver = new LeasedIdentityResolver<>(AwsCredentialsIdentity.class, () -> {
            var created = new TestResolver(creations.incrementAndGet(), closes);
            current.set(created);
            return created;
        });

        assertThrows(IllegalStateException.class, () -> resolver.resolveIdentity(Context.empty()));
        assertEquals(0, creations.get());
        AutoCloseable first = resolver.acquire();
        AutoCloseable second = resolver.acquire();

        assertEquals(1, creations.get());
        assertEquals("AK1", resolver.resolveIdentity(Context.empty()).identity().accessKeyId());
        var rejected = AwsCredentialsIdentity.create("AK1", "SK");
        resolver.invalidate(rejected);
        assertSame(rejected, current.get().invalidated);

        first.close();
        assertEquals(0, closes.get());
        second.close();
        assertEquals(1, closes.get());
        assertThrows(IllegalStateException.class, () -> resolver.resolveIdentity(Context.empty()));

        resolver.acquire().close();
        assertEquals(2, creations.get());
        assertEquals(2, closes.get());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void closesResolverCreatedWithIncompatibleIdentityType() {
        var wrongType = new CloseableTokenResolver();
        var resolver = new LeasedIdentityResolver<>(
                AwsCredentialsIdentity.class,
                () -> (IdentityResolver) wrongType);

        var error = assertThrows(IllegalStateException.class, resolver::acquire);

        assertTrue(error.getMessage().contains(TokenIdentity.class.getName()));
        assertTrue(wrongType.closed.get());
    }

    private static final class TestResolver
            implements IdentityResolver<AwsCredentialsIdentity>, AutoCloseable {
        private final int generation;
        private final AtomicInteger closes;
        private AwsCredentialsIdentity invalidated;

        TestResolver(int generation, AtomicInteger closes) {
            this.generation = generation;
            this.closes = closes;
        }

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
            return IdentityResult.of(AwsCredentialsIdentity.create("AK" + generation, "SK"));
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
            invalidated = rejectedIdentity;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private static final class CloseableTokenResolver
            implements IdentityResolver<TokenIdentity>, AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public IdentityResult<TokenIdentity> resolveIdentity(Context requestProperties) {
            return IdentityResult.of(TokenIdentity.create("token"));
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
