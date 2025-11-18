/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.handler.ssl.SslContextBuilder;
import java.io.Closeable;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A client transport that ends a serialized request and returns a response using Netty.
 * <p>
 * This client supports HTTP 1.1 and HTTP 2, and bidirectional streaming. Fine-tuning can be done by using the builder
 * for the client and specifically for H2, using the corresponding settings builder.
 */
public final class NettyHttpClientTransport implements Closeable, ClientTransport<HttpRequest, HttpResponse> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(NettyHttpClientTransport.class);
    private final NettyHttpClient client;

    NettyHttpClientTransport(Builder builder) {
        this.client = new NettyHttpClient(builder);
    }

    /**
     * Creates a builder that can be used to fine-tuning the client settings.
     *
     * @return A builder that can be used to fine-tuning the client settings.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        return client.send(request).join();
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public void close() {
        client.close();
    }

    /**
     * Configuration presets for different H2 usage scenarios. Each setting can set by using a
     * {@link H2ConfigurationBuilder},
     */
    public enum H2Preset {
        /**
         * Small payload uses a initial window size of 64KB, a high concurrency of 200 streams per connection and a
         * medium frame size of 16KB.
         */
        SMALL_PAYLOAD() {
            public void configure(H2ConfigurationBuilder builder) {
                // 64KB
                builder.initialWindowSize(64 * 1024);
                // Higher concurrency
                builder.maxConcurrentStreams(200);
                // Default frame size
                builder.maxFrameSize(16 * 1024);
            }
        },
        /**
         * Large payload uses a initial window size of 1Mb, a low concurrency of 50 streams per connection and a large
         * frame size of 64KB.
         */
        LARGE_PAYLOAD() {
            public void configure(H2ConfigurationBuilder builder) {
                // 1 MB
                builder.initialWindowSize(1024 * 1024);
                // Lower concurrency
                builder.maxConcurrentStreams(50);
                // Larger frames
                builder.maxFrameSize(64 * 1024);

            }
        },
        /**
         * High latency uses a large initial window size of 2Mb to reduce round trips and a large frame size of 64KB.
         */
        HIGH_LATENCY() {
            public void configure(H2ConfigurationBuilder builder) {
                // 2MB - compensate for round-trip delay
                builder.initialWindowSize(2 * 1024 * 1024);
                // Larger frames reduce overhead
                builder.maxFrameSize(64 * 1024);
            }
        };

        public abstract void configure(H2ConfigurationBuilder builder);
    }

    /**
     * Factory for creating an HTTP transport based on Netty.
     */
    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-netty";
        }

        @Override
        public NettyHttpClientTransport createTransport(Document node) {
            var builder = new Builder();
            if (node != null) {
                var versionNode = node.asStringMap().get("version");
                if (versionNode != null) {
                    var version = software.amazon.smithy.java.http.api.HttpVersion.from(versionNode.asString());
                    builder.httpVersion(version);
                }
            }
            return builder.build();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    /**
     * Netty HTTP client transport configuration.
     */
    public static class Configuration {
        private final HttpVersion httpVersion;
        private final int eventLoopGroupThreads;
        private final Duration connectTimeout;
        private final Duration readTimeout;
        private final Duration writeTimeout;
        private final Consumer<SslContextBuilder> sslContextModifier;
        private final H2Configuration h2Configuration;

        Configuration(Builder builder) {
            this.httpVersion = builder.httpVersion;
            this.eventLoopGroupThreads = builder.eventLoopGroupThreads;
            this.sslContextModifier = builder.sslContextModifier;
            this.connectTimeout = builder.connectTimeout;
            this.readTimeout = builder.readTimeout;
            this.writeTimeout = builder.writeTimeout;
            this.h2Configuration = builder.h2Configuration;
        }

        /**
         * Returns the configured HTTP version. By-default uses HTTP 1.1.
         *
         * @return the configured HTTP version
         */
        public HttpVersion httpVersion() {
            return httpVersion;
        }

        /**
         * Returns the configured number of threads for the event loop group. By-default uses Netty's default.
         *
         * @return the configured number of threads for the event loop group.
         */
        public int eventLoopGroupThreads() {
            return eventLoopGroupThreads;
        }

        /**
         * Returns the configured consumer used to fine-tune Netty's SSL context. By-default uses a no-op modifier.
         *
         * @return the configured consumer used to fine-tune Netty's SSL context
         */
        public Consumer<SslContextBuilder> sslContextModifier() {
            return sslContextModifier;
        }

        /**
         * Returns the configured connection timeout. By-default 5 seconds.
         *
         * @return the configured connection timeout
         */
        public Duration connectTimeout() {
            return connectTimeout;
        }

        /**
         * Returns the configured write timeout. By-default 30 seconds.
         *
         * @return the configured write timeout
         */
        public Duration writeTimeout() {
            return writeTimeout;
        }

        /**
         * Returns the configured read timeout. By-default 30 seconds.
         *
         * @return the configured read timeout
         */
        public Duration readTimeout() {
            return readTimeout;
        }

        /**
         * Returns the configured H2 settings for when using the HTTP 2 protocol.
         *
         * @return The configured H2 settings for when using the HTTP 2 protocol
         */
        public H2Configuration h2Configuration() {
            return h2Configuration;
        }
    }

    /**
     * Builder for the Netty HTTP client transport configuration.
     */
    public static class Builder {
        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(30);
        // Default HTTP version is HTTP 1.1.
        private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        // Zero will use the Netty default number of threads (processors * 2)
        private int eventLoopGroupThreads = 0;
        // Connection timeout, by default 5 seconds.
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        // Read timeout, by default 30 seconds.
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        // Write timeout, by default 30 seconds.
        private Duration writeTimeout = DEFAULT_WRITE_TIMEOUT;
        // No op modifier by default.
        private Consumer<SslContextBuilder> sslContextModifier = b -> {};
        // By default we use the LARGE_PAYLOAD preset.
        private H2Configuration h2Configuration = H2Configuration.DEFAULT;

        /**
         * Number of threads in the Netty event loop group. The default is zero which implies to use Netty's default
         * number equal to the number of processors times 2.
         *
         * @param eventLoopGroupThreads Number of threads for the Netty event loop group.
         * @return this builder instance for method chaining
         */
        public Builder eventLoopGroupThreads(int eventLoopGroupThreads) {
            if (eventLoopGroupThreads < 0) {
                throw new IllegalArgumentException(
                        "eventLoopGroupThreads cannot be negative, got: " + eventLoopGroupThreads);
            }
            this.eventLoopGroupThreads = eventLoopGroupThreads;
            return this;
        }

        /**
         * A Netty's SSL context consumer that can modify the builder prior to building it.
         *
         * @param sslContextModifier the SSL context modifier.
         * @return This builder
         */
        public Builder sslContextModifier(Consumer<SslContextBuilder> sslContextModifier) {
            this.sslContextModifier = Objects.requireNonNull(sslContextModifier, "sslContextModifier");
            return this;
        }

        /**
         * The time we wait until we consider the connection failed.
         *
         * @param connectTimeout The time we wait until we consider the connection failed
         * @return this builder instance for method chaining
         */
        public Builder connectTimeout(Duration connectTimeout) {
            if (Objects.requireNonNull(connectTimeout, "connectTimeout").isNegative()) {
                throw new IllegalArgumentException(
                        "connectTimeout cannot be negative, got: " + connectTimeout);
            }
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * The time we wait until we consider a read from the socket failed.
         *
         * @param readTimeout The time we wait until we consider a read from the socket failed
         * @return this builder instance for method chaining
         */
        public Builder readTimeout(Duration readTimeout) {
            if (Objects.requireNonNull(readTimeout, "readTimeout").isNegative()) {
                throw new IllegalArgumentException(
                        "readTimeout cannot be negative, got: " + readTimeout);
            }
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * The time we wait until we consider a write to the socket failed.
         *
         * @param writeTimeout The time we wait until we consider write to the socket failed
         * @return this builder instance for method chaining
         */
        public Builder writeTimeout(Duration writeTimeout) {
            if (Objects.requireNonNull(writeTimeout, "writeTimeout").isNegative()) {
                throw new IllegalArgumentException(
                        "writeTimeout cannot be negative, got: " + writeTimeout);
            }
            this.writeTimeout = writeTimeout;
            return this;
        }

        /**
         * The HTTP version configured for this client. By default, HTTP 1.1.
         *
         * @param httpVersion The HTTP version of this client.
         * @return this builder instance for method chaining
         */
        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = Objects.requireNonNull(httpVersion, "httpVersion");
            return this;
        }

        /**
         * A consumer that will be called with the H2 settings builder to modify any default or preset values.
         *
         * @param configurer A consumer used to configure the H2 settings.
         * @return this builder instance for method chaining
         */
        public Builder configureH2Settings(Consumer<H2ConfigurationBuilder> configurer) {
            var h2ConfigurationBuilder = new H2ConfigurationBuilder();
            configurer.accept(h2ConfigurationBuilder);
            h2Configuration = h2ConfigurationBuilder.build();
            return this;
        }

        /**
         * Use a preset for the H2 settings.
         *
         * @param h2Preset The preset used to configure the H2 settings.
         * @return this builder instance for method chaining
         */
        public Builder configureH2WithPreset(H2Preset h2Preset) {
            Objects.requireNonNull(h2Preset, "h2Preset");
            return configureH2Settings(h2Preset::configure);
        }

        public NettyHttpClientTransport build() {
            return new NettyHttpClientTransport(this);
        }

        Configuration buildConfiguration() {
            return new Configuration(this);
        }
    }

    /**
     * Configuration settings for H2 only.
     */
    public static class H2Configuration {
        private static final H2Configuration DEFAULT;

        static {
            var builder = new H2ConfigurationBuilder();
            H2Preset.LARGE_PAYLOAD.configure(builder);
            DEFAULT = builder.build();
        }

        private final long maxConcurrentStreams;
        private final int initialWindowSize;
        private final int maxFrameSize;
        private final H2ConnectionMode connectionMode;

        private H2Configuration(H2ConfigurationBuilder builder) {
            this.maxConcurrentStreams = builder.maxConcurrentStreams;
            this.initialWindowSize = builder.initialWindowSize;
            this.maxFrameSize = builder.maxFrameSize;
            this.connectionMode = builder.connectionMode;
        }

        /**
         * Gets the maximum number of concurrent streams allowed on a single HTTP/2 connection.
         *
         * @return the maximum number of concurrent streams allowed on a single HTTP/2 connection.
         */
        public long maxConcurrentStreams() {
            return maxConcurrentStreams;
        }

        /**
         * Gets the initial window size for HTTP/2 flow control on new streams.
         *
         * @return the initial window size for HTTP/2 flow control on new streams.
         */
        public int initialWindowSize() {
            return initialWindowSize;
        }

        /**
         * Gets the maximum size of HTTP/2 frames that can be received.
         *
         * @return the maximum size of HTTP/2 frames that can be received.
         */
        public int maxFrameSize() {
            return maxFrameSize;
        }

        /**
         * Gets the mode to establish HTTP/2 connections.
         *
         * @return the mode to establish HTTP/2 connections.
         */
        public H2ConnectionMode connectionMode() {
            return connectionMode;
        }
    }

    /**
     * A H2 settings configuration builder.
     */
    public static class H2ConfigurationBuilder {
        /**
         * H2 default for max concurrent streams is 2^32 - 1 (max unsigned 32-bit integer).
         */
        public static final long DEFAULT_MAX_CONCURRENT_STREAMS = (1L << 32) - 1;
        /**
         * H2 default for initial window size is 64 KB
         */
        public static final int DEFAULT_INITIAL_WINDOW_SIZE = 64 * 1024;
        /**
         * H2 default for max frame size is 16 KB
         */
        public static final int DEFAULT_MAX_FRAME_SIZE = 16 * 1024;

        private long maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
        private int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
        private int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
        private H2ConnectionMode connectionMode = H2ConnectionMode.AUTO;

        /**
         * Sets the maximum number of concurrent streams allowed on a single HTTP/2 connection.
         *
         * <p> This limit prevents resource exhaustion by controlling how many simultaneous requests can be
         * processed. Higher values enable better connection utilization but increase memory and CPU overhead. Lower
         * values reduce resource usage but may limit throughput for clients making many parallel requests. Default is
         * unlimited (2^32-1), but production systems typically set values between 25-500 based on expected load and
         * available resources.
         *
         * @param maxConcurrentStreams the maximum concurrent streams, must be positive
         * @return this builder instance for method chaining
         */
        public H2ConfigurationBuilder maxConcurrentStreams(long maxConcurrentStreams) {
            if (maxConcurrentStreams <= 0) {
                throw new IllegalArgumentException(
                        "maxStreamsPerConnection cannot be negative or zero, got: " + maxConcurrentStreams);
            }

            this.maxConcurrentStreams = maxConcurrentStreams;
            return this;
        }

        /**
         * Sets the initial window size for HTTP/2 flow control on new streams.
         *
         * <p> This value determines how much data can be sent on a stream before requiring a WINDOW_UPDATE frame from
         * the receiver. Larger values improve throughput by reducing round-trips but increase memory usage. Smaller
         * values conserve memory but may limit performance on high-bandwidth or high-latency connections.  Default is
         * 65,535 bytes (64KB). Typical values range from 32KB (memory-constrained) to 2MB (high-throughput scenarios).
         *
         * @param initialWindowSize the initial window size in bytes, must be positive
         * @return this builder instance for method chaining
         */
        public H2ConfigurationBuilder initialWindowSize(int initialWindowSize) {
            if (initialWindowSize <= 0) {
                throw new IllegalArgumentException(
                        "initialWindowSize cannot be negative or zero, got: " + initialWindowSize);
            }
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        /**
         * Sets the maximum size of HTTP/2 frames that can be received.
         *
         * <p> Larger frame sizes reduce protocol overhead by packing more data per frame but increase memory
         * requirements and may impact latency for small messages.  Smaller frames provide better multiplexing
         * granularity and lower memory usage per frame. Default is 16,384 bytes (16KB). Valid range is 16KB to 16MB,
         * with typical production values between 16KB (low-latency) and 64KB (high-throughput).
         *
         * @param maxFrameSize the maximum frame size in bytes, must be between 16384 and 16777215
         * @return this builder instance for method chaining
         */
        public H2ConfigurationBuilder maxFrameSize(int maxFrameSize) {
            if (maxFrameSize < 0) {
                throw new IllegalArgumentException("maxFrameSize cannot be negative, got: " + maxFrameSize);
            }
            if (maxFrameSize < 16 * 1024) {
                throw new IllegalArgumentException("maxFrameSize cannot be less than 16KB, got: " + maxFrameSize);
            }
            if (maxFrameSize > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("maxFrameSize cannot be grater than 16MB, got: " + maxFrameSize);
            }
            this.maxFrameSize = maxFrameSize;
            return this;
        }

        /**
         * The mode to establish HTTP/2 connections.
         *
         * @param connectionMode  connection mode to establish HTTP/2 connections.
         * @return this builder instance for method chaining
         */
        public H2ConfigurationBuilder connectionMode(H2ConnectionMode connectionMode) {
            this.connectionMode = Objects.requireNonNull(connectionMode, "connectionMode");
            return this;
        }

        public H2Configuration build() {
            return new H2Configuration(this);
        }
    }

    /**
     * The connection mode for establishing HTTP/2 connections.
     */
    enum H2ConnectionMode {
        /**
         * Uses ALPN over https and prior knowledge over http.
         */
        AUTO,
        /**
         * Negotiate using Application Layer Protocol Negotiation. This mode requires HTTPS.
         */
        ALPN,
        /**
         * Use prior knowledge.
         */
        PRIOR_KNOWLEDGE
    }
}
