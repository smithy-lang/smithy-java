/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.context.Context;

/**
 * Shares a lazily created resolver while leases are active and recreates it after the last lease is closed.
 */
final class LeasedIdentityResolver<I extends Identity> implements IdentityResolver<I> {

    private final Class<I> identityType;
    private final Supplier<? extends IdentityResolver<I>> factory;
    private IdentityResolver<I> delegate;
    private AutoCloseable closeable;
    private int leases;

    LeasedIdentityResolver(Class<I> identityType, Supplier<? extends IdentityResolver<I>> factory) {
        this.identityType = Objects.requireNonNull(identityType, "identityType");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    synchronized AutoCloseable acquire() {
        if (delegate == null) {
            IdentityResolver<I> created = Objects.requireNonNull(factory.get(), "Identity resolver must not be null");
            try {
                Class<I> createdType = created.identityType();
                if (!identityType.isAssignableFrom(createdType)) {
                    throw new IllegalStateException(
                            "Identity resolver factory returned " + createdType.getName()
                                    + " when " + identityType.getName() + " was required");
                }
                if (!(created instanceof AutoCloseable createdCloseable)) {
                    throw new IllegalStateException("Leased identity resolver must be AutoCloseable");
                }
                delegate = created;
                closeable = createdCloseable;
            } catch (RuntimeException | Error error) {
                closeAfterFailedAcquire(created, error);
                throw error;
            }
        }
        leases++;
        return new Lease(this);
    }

    @Override
    public IdentityResult<I> resolveIdentity(Context requestProperties) {
        return activeDelegate().resolveIdentity(requestProperties);
    }

    @Override
    public Class<I> identityType() {
        return identityType;
    }

    @Override
    public void invalidate(I rejectedIdentity) {
        IdentityResolver<I> current;
        synchronized (this) {
            current = delegate;
        }
        if (current != null) {
            current.invalidate(rejectedIdentity);
        }
    }

    private synchronized IdentityResolver<I> activeDelegate() {
        if (delegate == null) {
            throw new IllegalStateException("Identity resolver has no active client lease");
        }
        return delegate;
    }

    private synchronized void release() throws Exception {
        if (leases == 0) {
            throw new IllegalStateException("Identity resolver lease count is already zero");
        }
        if (--leases == 0) {
            AutoCloseable released = closeable;
            delegate = null;
            closeable = null;
            released.close();
        }
    }

    private static void closeAfterFailedAcquire(Object created, Throwable failure) {
        if (created instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception closeError) {
                failure.addSuppressed(closeError);
            } catch (Error closeError) {
                failure.addSuppressed(closeError);
            }
        }
    }

    private static final class Lease implements AutoCloseable {
        private final LeasedIdentityResolver<?> owner;
        private boolean closed;

        Lease(LeasedIdentityResolver<?> owner) {
            this.owner = owner;
        }

        @Override
        public synchronized void close() throws Exception {
            if (!closed) {
                closed = true;
                owner.release();
            }
        }
    }
}
