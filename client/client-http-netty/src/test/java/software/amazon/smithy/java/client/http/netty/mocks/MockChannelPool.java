/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.mocks;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Good enough™ mock of a channel pool.
 */
public class MockChannelPool implements ChannelPool {
    private final EventLoopGroup eventLoopGroup;
    private final Function<Promise<Channel>, Future<Channel>> onAcquire;
    private final BiFunction<Channel, Promise<Void>, Future<Void>> onRelease;
    private final AtomicBoolean closed = new AtomicBoolean();

    MockChannelPool(
            EventLoopGroup eventLoopGroup,
            Function<Promise<Channel>, Future<Channel>> onAcquire,
            BiFunction<Channel, Promise<Void>, Future<Void>> onRelease
    ) {
        this.eventLoopGroup = eventLoopGroup;
        this.onAcquire = onAcquire;
        this.onRelease = onRelease;
    }

    public MockChannelPool(Builder builder) {
        this.eventLoopGroup = Objects.requireNonNull(builder.eventLoopGroup);
        this.onAcquire = Objects.requireNonNull(builder.onAcquire);
        this.onRelease = Objects.requireNonNull(builder.onRelease);
    }

    @Override
    public Future<Channel> acquire() {
        return acquire(eventLoopGroup.next().newPromise());
    }

    @Override
    public Future<Channel> acquire(Promise<Channel> promise) {
        return onAcquire.apply(promise);
    }

    @Override
    public Future<Void> release(Channel channel) {
        return release(channel, eventLoopGroup.next().newPromise());
    }

    @Override
    public Future<Void> release(Channel channel, Promise<Void> promise) {
        return onRelease.apply(channel, promise);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            throw new IllegalStateException("Already closed");
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EventLoopGroup eventLoopGroup;
        private Function<Promise<Channel>, Future<Channel>> onAcquire;
        private BiFunction<Channel, Promise<Void>, Future<Void>> onRelease = (c, p) -> p;

        public Builder eventLoopGroup(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public Builder onAcquireFail(Throwable throwable) {
            return onAcquire(p -> {
                p.setFailure(throwable);
                return p;
            });
        }

        public Builder onAcquireSucceed(Channel channel) {
            return onAcquire(p -> {
                p.setSuccess(channel);
                return p;
            });
        }

        public Builder onAcquire(Function<Promise<Channel>, Future<Channel>> onAcquire) {
            this.onAcquire = onAcquire;
            return this;
        }

        public Builder onRelease(BiFunction<Channel, Promise<Void>, Future<Void>> onRelease) {
            this.onRelease = onRelease;
            return this;
        }

        public MockChannelPool build() {
            return new MockChannelPool(this);
        }
    }
}
