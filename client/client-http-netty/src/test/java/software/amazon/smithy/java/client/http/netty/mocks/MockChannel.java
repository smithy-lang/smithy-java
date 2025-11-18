/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.mocks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.DefaultAttributeMap;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * Good enough™ mock of a channel. It can be used to assert handlers and their order
 * as configured. Most operations will throw {@link UnsupportedOperationException}. Override
 * this class to mock the desired behavior.
 */
public class MockChannel implements Channel {
    private boolean wasClosed = false;
    private final ChannelId channelId;
    private final EventLoop eventLoop;
    private final Channel parent;
    private final boolean isOpen;
    private final boolean isActive;
    private final boolean isRegistered;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private final MockChannelPipeline pipeline;
    private final AttributeMap attributeMap;

    public MockChannel(Builder builder) {
        this.channelId = Objects.requireNonNull(builder.channelId, "channelId");
        this.parent = builder.parent;
        this.isOpen = builder.isOpen;
        this.isActive = builder.isActive;
        this.isRegistered = builder.isRegistered;
        this.localAddress = builder.localAddress;
        this.remoteAddress = builder.remoteAddress;
        this.pipeline = builder.pipeline;
        this.eventLoop = builder.eventLoop;
        this.attributeMap = builder.attributeMap;
    }

    @Override
    public ChannelId id() {
        return channelId;
    }

    @Override
    public EventLoop eventLoop() {
        return eventLoop;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelConfig config() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isRegistered() {
        return isRegistered;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public ChannelMetadata metadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public ChannelFuture closeFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Unsafe unsafe() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    public MockChannelPipeline mockPipeline() {
        return pipeline;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return attributeMap.attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return attributeMap.hasAttr(key);
    }

    @Override
    public ChannelFuture close() {
        var result = new DefaultChannelPromise(this);
        wasClosed = true;
        result.setSuccess();
        return result;
    }

    @Override
    public int compareTo(Channel o) {
        return 0;
    }

    public boolean isClosed() {
        return wasClosed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Stream extends MockChannel implements Http2StreamChannel {
        private final MockHttp2FrameStream frameStream;
        private Stream(Builder builder, int streamId) {
            super(builder);
            this.frameStream = new MockHttp2FrameStream(streamId);
        }

        @Override
        public Http2FrameStream stream() {
            return frameStream;
        }
    }

    static class MockHttp2FrameStream implements Http2FrameStream {
        private final int streamId;

        MockHttp2FrameStream(int streamId) {
            this.streamId = streamId;
        }

        @Override
        public int id() {
            return streamId;
        }

        @Override
        public Http2Stream.State state() {
            throw new UnsupportedOperationException();
        }
    }

    public static class Builder {
        private ChannelId channelId;
        private EventLoop eventLoop;
        private Channel parent;
        private boolean isOpen = true;
        private boolean isActive = true;
        private boolean isRegistered = true;
        private SocketAddress localAddress = new InetSocketAddress("127.0.0.1", 12345);
        private SocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 80);
        private MockChannelPipeline pipeline;
        private AttributeMap attributeMap = new DefaultAttributeMap();

        public Builder channelId(ChannelId channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder eventLoop(EventLoop eventLoop) {
            this.eventLoop = eventLoop;
            return this;
        }

        public Builder parent(Channel parent) {
            this.parent = parent;
            return this;
        }

        public Builder isOpen(boolean isOpen) {
            this.isOpen = isOpen;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder isRegistered(boolean isRegistered) {
            this.isRegistered = isRegistered;
            return this;
        }

        public Builder localAddress(SocketAddress localAddress) {
            this.localAddress = localAddress;
            return this;
        }

        public Builder remoteAddress(SocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder pipeline(MockChannelPipeline pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public <T> Builder attr(AttributeKey<T> key, T value) {
            this.attributeMap.attr(key).set(value);
            return this;
        }

        public MockChannel build() {
            if (channelId == null) {
                this.channelId = DefaultChannelId.newInstance();
            }
            if (pipeline == null) {
                this.pipeline = new MockChannelPipeline();
            }
            return new MockChannel(this);
        }

        public Stream buildStream() {
            if (channelId == null) {
                this.channelId = DefaultChannelId.newInstance();
            }
            if (pipeline == null) {
                this.pipeline = new MockChannelPipeline();
            }
            return new Stream(this, 1);
        }

        public Stream buildStream(int streamId) {
            if (channelId == null) {
                this.channelId = DefaultChannelId.newInstance();
            }
            if (pipeline == null) {
                this.pipeline = new MockChannelPipeline();
            }
            return new Stream(this, streamId);
        }
    }
}
