/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.mocks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.internal.StringUtil;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Good enough™ mock of a channel pipeline. It can be used to assert handlers and their order
 * as configured. Most operations will throw {@link UnsupportedOperationException}. Override
 * this class to mock the desired behavior.
 */
public class MockChannelPipeline implements ChannelPipeline {

    private final LinkedHashMap<String, ChannelHandler> channelHandlers = new LinkedHashMap<>();

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        channelHandlers.putFirst(name, handler);
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        channelHandlers.putLast(name, handler);
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        for (ChannelHandler handler : handlers) {
            channelHandlers.putFirst(findName(handler.getClass()), handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        for (ChannelHandler handler : handlers) {
            channelHandlers.putLast(findName(handler.getClass()), handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler removeFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler removeLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler first() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler last() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandler get(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandlerContext context(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Channel channel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(channelHandlers.keySet());
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        return new LinkedHashMap<>(this.channelHandlers);
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelOutboundInvoker read() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPipeline flush() {
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelPromise voidPromise() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return channelHandlers.entrySet().iterator();
    }

    public boolean containsHandler(Class<? extends ChannelHandler> handlerType) {
        return channelHandlers.containsKey(generateName(handlerType, 0));
    }

    public boolean containsInOrder(Class<?>... handlerTypes) {
        if (channelHandlers.size() != handlerTypes.length) {
            return false;
        }
        var index = 0;
        for (var registeredHandler : channelHandlers.values()) {
            var expectedHandlerType = handlerTypes[index++];
            if (registeredHandler.getClass() != expectedHandlerType) {
                return false;
            }
        }
        return true;
    }

    private String findName(Class<? extends ChannelHandler> clazz) {
        var index = 0;
        while (true) {
            var name = generateName(clazz, index++);
            if (!channelHandlers.containsKey(name)) {
                return name;
            }
            // Make sure we don't end up in an infinite loop.
            if (index >= 20) {
                throw new IllegalStateException("Too many channel handlers with the same name prefix: " + name);
            }
        }
    }

    private static String generateName(Class<?> handlerType, int index) {
        return StringUtil.simpleClassName(handlerType) + "#" + index;
    }

}
