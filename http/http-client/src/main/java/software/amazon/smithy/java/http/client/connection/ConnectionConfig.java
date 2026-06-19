/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import software.amazon.smithy.java.http.client.HttpClientListener;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Immutable connection configuration used to create a {@link ConnectionPool}.
 */
public record ConnectionConfig(
        int maxTotalConnections,
        int maxConnectionsPerRoute,
        int h2StreamsPerConnection,
        int h2InitialWindowSize,
        int h2MaxFrameSize,
        int h2BufferSize,
        Duration maxIdleTime,
        Duration acquireTimeout,
        Duration connectTimeout,
        Duration tlsNegotiationTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        SSLContext sslContext,
        SSLParameters sslParameters,
        TlsProvider tlsProvider,
        HttpVersionPolicy versionPolicy,
        DnsResolver dnsResolver,
        HttpSocketFactory socketFactory,
        Integer socketReceiveBufferSize,
        Integer socketSendBufferSize,
        int tlsReadBufferSize,
        int tlsWriteBufferSize,
        List<HttpClientListener> listeners) {
    public ConnectionConfig {
        if (maxTotalConnections <= 0) {
            throw new IllegalArgumentException("maxTotalConnections must be positive: " + maxTotalConnections);
        }
        if (maxConnectionsPerRoute <= 0) {
            throw new IllegalArgumentException("maxConnectionsPerRoute must be positive: " + maxConnectionsPerRoute);
        }
        if (maxTotalConnections < maxConnectionsPerRoute) {
            throw new IllegalArgumentException(
                    "maxTotalConnections (" + maxTotalConnections + ") must be >= maxConnectionsPerRoute ("
                            + maxConnectionsPerRoute + ")");
        }
        if (h2StreamsPerConnection <= 0) {
            throw new IllegalArgumentException("h2StreamsPerConnection must be positive: " + h2StreamsPerConnection);
        }
        if (h2InitialWindowSize <= 0) {
            throw new IllegalArgumentException("h2InitialWindowSize must be positive: " + h2InitialWindowSize);
        }
        if (h2MaxFrameSize < 16384 || h2MaxFrameSize > 16777215) {
            throw new IllegalArgumentException("h2MaxFrameSize must be between 16384 and 16777215: " + h2MaxFrameSize);
        }
        if (h2BufferSize < 16 * 1024) {
            throw new IllegalArgumentException("h2BufferSize must be at least 16KB: " + h2BufferSize);
        }
        requireNonNegative(maxIdleTime, "maxIdleTime");
        if (maxIdleTime.isZero()) {
            throw new IllegalArgumentException("maxIdleTime must be positive: " + maxIdleTime);
        }
        requireNonNegative(acquireTimeout, "acquireTimeout");
        requireNonNegative(connectTimeout, "connectTimeout");
        requireNonNegative(tlsNegotiationTimeout, "tlsNegotiationTimeout");
        requireNonNegative(readTimeout, "readTimeout");
        requireNonNegative(writeTimeout, "writeTimeout");
        Objects.requireNonNull(versionPolicy, "versionPolicy");
        // socketFactory may be null, meaning "use the buffer-applying default" (see HttpConnectionPool).
        if (socketReceiveBufferSize != null && (socketReceiveBufferSize < -1 || socketReceiveBufferSize == 0)) {
            throw new IllegalArgumentException(
                    "socketReceiveBufferSize must be positive or -1: " + socketReceiveBufferSize);
        }
        if (socketSendBufferSize != null && (socketSendBufferSize < -1 || socketSendBufferSize == 0)) {
            throw new IllegalArgumentException("socketSendBufferSize must be positive or -1: " + socketSendBufferSize);
        }
        if (tlsReadBufferSize <= 0) {
            throw new IllegalArgumentException("tlsReadBufferSize must be positive: " + tlsReadBufferSize);
        }
        if (tlsWriteBufferSize <= 0) {
            throw new IllegalArgumentException("tlsWriteBufferSize must be positive: " + tlsWriteBufferSize);
        }

        listeners = List.copyOf(listeners);
        if (sslContext == null) {
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed to get default SSLContext", e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void requireNonNegative(Duration duration, String name) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be non-negative: " + duration);
        }
    }

    public static class Builder {
        int maxTotalConnections = 256;
        int maxConnectionsPerRoute = 256;
        int h2StreamsPerConnection = 100;
        int h2InitialWindowSize = 65535;
        int h2MaxFrameSize = 16384;
        int h2BufferSize = 256 * 1024;

        Duration maxIdleTime = Duration.ofMinutes(2);
        Duration acquireTimeout = Duration.ofSeconds(30);
        Duration connectTimeout = Duration.ofSeconds(10);
        Duration tlsNegotiationTimeout = Duration.ofSeconds(10);
        Duration readTimeout = Duration.ofSeconds(30);
        Duration writeTimeout = Duration.ofSeconds(30);
        SSLContext sslContext;
        SSLParameters sslParameters;
        TlsProvider tlsProvider;
        HttpVersionPolicy versionPolicy = HttpVersionPolicy.AUTOMATIC;
        DnsResolver dnsResolver;
        HttpSocketFactory socketFactory; // null => HttpConnectionPool synthesizes the default
        Integer socketReceiveBufferSize;
        Integer socketSendBufferSize;
        int tlsReadBufferSize = 16 * 1024;
        int tlsWriteBufferSize = 16 * 1024;
        final List<HttpClientListener> listeners = new LinkedList<>();

        protected Builder() {}

        public Builder maxConnectionsPerRoute(int max) {
            this.maxConnectionsPerRoute = max;
            return this;
        }

        public Builder maxTotalConnections(int max) {
            this.maxTotalConnections = max;
            return this;
        }

        public Builder maxIdleTime(Duration duration) {
            this.maxIdleTime = duration;
            return this;
        }

        public Builder acquireTimeout(Duration timeout) {
            this.acquireTimeout = timeout;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder tlsNegotiationTimeout(Duration timeout) {
            this.tlsNegotiationTimeout = timeout;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        public Builder sslContext(SSLContext context) {
            this.sslContext = context;
            return this;
        }

        public Builder sslParameters(SSLParameters parameters) {
            this.sslParameters = parameters;
            return this;
        }

        public Builder tlsProvider(TlsProvider provider) {
            this.tlsProvider = provider;
            return this;
        }

        public Builder httpVersionPolicy(HttpVersionPolicy policy) {
            this.versionPolicy = Objects.requireNonNull(policy, "httpVersionPolicy cannot be null");
            return this;
        }

        public Builder dnsResolver(DnsResolver resolver) {
            this.dnsResolver = Objects.requireNonNull(resolver, "dnsResolver must not be null");
            return this;
        }

        public Builder socketFactory(HttpSocketFactory socketFactory) {
            this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
            return this;
        }

        public Builder socketReceiveBufferSize(int bytes) {
            this.socketReceiveBufferSize = bytes;
            return this;
        }

        public Builder socketSendBufferSize(int bytes) {
            this.socketSendBufferSize = bytes;
            return this;
        }

        public Builder tlsReadBufferSize(int bytes) {
            this.tlsReadBufferSize = bytes;
            return this;
        }

        public Builder tlsWriteBufferSize(int bytes) {
            this.tlsWriteBufferSize = bytes;
            return this;
        }

        public Builder h2InitialWindowSize(int windowSize) {
            this.h2InitialWindowSize = windowSize;
            return this;
        }

        public Builder h2MaxFrameSize(int frameSize) {
            this.h2MaxFrameSize = frameSize;
            return this;
        }

        public Builder h2StreamsPerConnection(int streams) {
            this.h2StreamsPerConnection = streams;
            return this;
        }

        public Builder h2BufferSize(int bufferSize) {
            this.h2BufferSize = bufferSize;
            return this;
        }

        public Builder addListener(HttpClientListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public Builder addListenerFirst(HttpClientListener listener) {
            listeners.addFirst(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(
                    maxTotalConnections,
                    maxConnectionsPerRoute,
                    h2StreamsPerConnection,
                    h2InitialWindowSize,
                    h2MaxFrameSize,
                    h2BufferSize,
                    maxIdleTime,
                    acquireTimeout,
                    connectTimeout,
                    tlsNegotiationTimeout,
                    readTimeout,
                    writeTimeout,
                    sslContext,
                    sslParameters,
                    tlsProvider,
                    versionPolicy,
                    dnsResolver,
                    socketFactory,
                    socketReceiveBufferSize,
                    socketSendBufferSize,
                    tlsReadBufferSize,
                    tlsWriteBufferSize,
                    listeners);
        }
    }
}
