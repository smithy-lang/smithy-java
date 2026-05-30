/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.smithy;

import java.time.Duration;
import software.amazon.smithy.java.client.http.HttpTransportConfig;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * Transport configuration for the Smithy HTTP client, extending common settings
 * with connection pool and HTTP/2 tuning options.
 */
public final class SmithyHttpTransportConfig extends HttpTransportConfig {

    private Integer maxConnections;
    private Integer maxConnectionsPerRoute;
    private Duration maxIdleTime;
    private Integer h2StreamsPerConnection;
    private Integer h2InitialWindowSize;
    private HttpVersionPolicy httpVersionPolicy;
    private Integer socketReceiveBufferSize;
    private Integer socketSendBufferSize;

    public Integer maxConnections() {
        return maxConnections;
    }

    public SmithyHttpTransportConfig maxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    /**
     * Maximum idle connections retained per route (host+port+proxy). When unset, the route limit
     * defaults to {@link #maxConnections}.
     */
    public Integer maxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public SmithyHttpTransportConfig maxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        return this;
    }

    /**
     * SO_RCVBUF for new connection sockets. Larger values help low-concurrency throughput on
     * high-bandwidth links; smaller values bound per-connection bufferbloat at high concurrency.
     * 64 KiB is the library default. Pass {@code -1} to defer to the kernel's autotune. See
     * {@code HttpConnectionPoolBuilder#socketReceiveBufferSize(int)} for full guidance.
     */
    public Integer socketReceiveBufferSize() {
        return socketReceiveBufferSize;
    }

    public SmithyHttpTransportConfig socketReceiveBufferSize(int bytes) {
        this.socketReceiveBufferSize = bytes;
        return this;
    }

    /**
     * SO_SNDBUF for new connection sockets. 64 KiB is the library default. Pass {@code -1} to
     * defer to the kernel's autotune.
     */
    public Integer socketSendBufferSize() {
        return socketSendBufferSize;
    }

    public SmithyHttpTransportConfig socketSendBufferSize(int bytes) {
        this.socketSendBufferSize = bytes;
        return this;
    }

    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    public SmithyHttpTransportConfig maxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    public Integer h2StreamsPerConnection() {
        return h2StreamsPerConnection;
    }

    public SmithyHttpTransportConfig h2StreamsPerConnection(int h2StreamsPerConnection) {
        this.h2StreamsPerConnection = h2StreamsPerConnection;
        return this;
    }

    public Integer h2InitialWindowSize() {
        return h2InitialWindowSize;
    }

    public SmithyHttpTransportConfig h2InitialWindowSize(int h2InitialWindowSize) {
        this.h2InitialWindowSize = h2InitialWindowSize;
        return this;
    }

    public HttpVersionPolicy httpVersionPolicy() {
        return httpVersionPolicy;
    }

    public SmithyHttpTransportConfig httpVersionPolicy(HttpVersionPolicy httpVersionPolicy) {
        this.httpVersionPolicy = httpVersionPolicy;
        return this;
    }

    @Override
    public SmithyHttpTransportConfig fromDocument(Document doc) {
        super.fromDocument(doc);
        var config = doc.asStringMap();

        var maxConns = config.get("maxConnections");
        if (maxConns != null) {
            this.maxConnections = maxConns.asInteger();
        }

        var maxConnsPerRoute = config.get("maxConnectionsPerRoute");
        if (maxConnsPerRoute != null) {
            this.maxConnectionsPerRoute = maxConnsPerRoute.asInteger();
        }

        var recvBuf = config.get("socketReceiveBufferSize");
        if (recvBuf != null) {
            this.socketReceiveBufferSize = recvBuf.asInteger();
        }

        var sendBuf = config.get("socketSendBufferSize");
        if (sendBuf != null) {
            this.socketSendBufferSize = sendBuf.asInteger();
        }

        var idle = config.get("maxIdleTimeMs");
        if (idle != null) {
            this.maxIdleTime = Duration.ofMillis(idle.asLong());
        }

        var h2Streams = config.get("h2StreamsPerConnection");
        if (h2Streams != null) {
            this.h2StreamsPerConnection = h2Streams.asInteger();
        }

        var h2Window = config.get("h2InitialWindowSize");
        if (h2Window != null) {
            this.h2InitialWindowSize = h2Window.asInteger();
        }

        var versionPolicy = config.get("httpVersionPolicy");
        if (versionPolicy != null) {
            this.httpVersionPolicy = HttpVersionPolicy.valueOf(versionPolicy.asString());
        }

        return this;
    }
}
