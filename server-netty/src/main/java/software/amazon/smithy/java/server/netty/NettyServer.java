/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.core.DefaultOrchestratorImpl;
import software.amazon.smithy.java.server.core.Orchestrator;

final class NettyServer implements Server {

    private final ServerBootstrap bootstrap;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final URI endpoint;

    NettyServer(NettyServerBuilder builder) {
        var bootstrap = new ServerBootstrap();

        Orchestrator orch = new DefaultOrchestratorImpl(builder.services.get(0), builder.numWorkers, List.of());
        NettyHandler handler = new NettyHandler(orch);
        Consumer<ChannelPipeline> handlerInstaller = (pipeline) -> {
            pipeline.addLast(handler);
        };

        bootstrap.childHandler(new NettyChannelInitializer(handlerInstaller));
        if (Epoll.isAvailable()) {
            this.bossGroup = new EpollEventLoopGroup(1);
            this.workerGroup = new EpollEventLoopGroup(builder.numWorkers);
            bootstrap.channelFactory(EpollServerSocketChannel::new);
        } else if (KQueue.isAvailable()) {
            this.bossGroup = new KQueueEventLoopGroup(1);
            this.workerGroup = new KQueueEventLoopGroup(builder.numWorkers);
            bootstrap.channelFactory(KQueueServerSocketChannel::new);
        } else {
            throw new IllegalStateException("No suitable transport found");
        }

        this.bootstrap = bootstrap;
        this.endpoint = builder.defaultEndpoint;


    }


    @Override
    public void start() {
        try {
            bootstrap.group(bossGroup, workerGroup)
                .localAddress(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()))
                .bind()
                .sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Server started on port " + endpoint.getPort());
    }

    @Override
    public void stop() {
        System.out.println("Stopping Netty Server");
    }
}
