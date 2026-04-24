/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.time.Duration;
import software.amazon.smithy.java.client.http.HttpTransportConfig;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Configuration for {@link NettyHttpClientTransport}.
 */
public final class NettyHttpTransportConfig extends HttpTransportConfig {

    private int maxConnectionsPerHost = 20;
    private int h2StreamsPerConnection = 100;
    private Duration maxIdleTime = Duration.ofMinutes(2);
    private Duration acquireTimeout = Duration.ofSeconds(30);
    private HttpVersionPolicy httpVersionPolicy = HttpVersionPolicy.AUTOMATIC;
    private int eventLoopThreads = 0; // 0 => Runtime.getRuntime().availableProcessors()
    private int initialWindowSize = 16 * 1024 * 1024;
    private int maxFrameSize = 64 * 1024; // 64 KB — H2 default is 16 KB, 64 KB is a safe larger default
    private int writeBufferLowWater = 32 * 1024;
    private int writeBufferHighWater = 256 * 1024;

    public int maxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public NettyHttpTransportConfig maxConnectionsPerHost(int v) {
        this.maxConnectionsPerHost = v;
        return this;
    }

    public int h2StreamsPerConnection() {
        return h2StreamsPerConnection;
    }

    public NettyHttpTransportConfig h2StreamsPerConnection(int v) {
        this.h2StreamsPerConnection = v;
        return this;
    }

    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    public NettyHttpTransportConfig maxIdleTime(Duration v) {
        this.maxIdleTime = v;
        return this;
    }

    public Duration acquireTimeout() {
        return acquireTimeout;
    }

    public NettyHttpTransportConfig acquireTimeout(Duration v) {
        this.acquireTimeout = v;
        return this;
    }

    public HttpVersionPolicy httpVersionPolicy() {
        return httpVersionPolicy;
    }

    public NettyHttpTransportConfig httpVersionPolicy(HttpVersionPolicy v) {
        this.httpVersionPolicy = v;
        return this;
    }

    public int eventLoopThreads() {
        return eventLoopThreads;
    }

    public NettyHttpTransportConfig eventLoopThreads(int v) {
        this.eventLoopThreads = v;
        return this;
    }

    public int initialWindowSize() {
        return initialWindowSize;
    }

    public NettyHttpTransportConfig initialWindowSize(int v) {
        this.initialWindowSize = v;
        return this;
    }

    public int maxFrameSize() {
        return maxFrameSize;
    }

    public NettyHttpTransportConfig maxFrameSize(int v) {
        this.maxFrameSize = v;
        return this;
    }

    public int writeBufferLowWater() {
        return writeBufferLowWater;
    }

    public int writeBufferHighWater() {
        return writeBufferHighWater;
    }

    public NettyHttpTransportConfig writeBufferWatermarks(int low, int high) {
        this.writeBufferLowWater = low;
        this.writeBufferHighWater = high;
        return this;
    }

    @Override
    public NettyHttpTransportConfig fromDocument(Document doc) {
        super.fromDocument(doc);
        var config = doc.asStringMap();

        var maxConns = config.get("maxConnectionsPerHost");
        if (maxConns != null) {
            this.maxConnectionsPerHost = maxConns.asInteger();
        }

        var streams = config.get("h2StreamsPerConnection");
        if (streams != null) {
            this.h2StreamsPerConnection = streams.asInteger();
        }

        var idle = config.get("maxIdleTimeMs");
        if (idle != null) {
            this.maxIdleTime = Duration.ofMillis(idle.asLong());
        }

        var policy = config.get("httpVersionPolicy");
        if (policy != null) {
            this.httpVersionPolicy = HttpVersionPolicy.valueOf(policy.asString());
        }

        var threads = config.get("eventLoopThreads");
        if (threads != null) {
            this.eventLoopThreads = threads.asInteger();
        }

        var window = config.get("h2InitialWindowSize");
        if (window != null) {
            this.initialWindowSize = window.asInteger();
        }

        return this;
    }
}
