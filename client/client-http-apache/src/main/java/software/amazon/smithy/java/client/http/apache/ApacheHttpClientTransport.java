/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Client transport backed by Apache HttpClient 5 async with a blocking response facade.
 */
public final class ApacheHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {
    private final ApacheHttpTransportConfig config;
    private final CloseableHttpAsyncClient client;

    public ApacheHttpClientTransport() {
        this(new ApacheHttpTransportConfig());
    }

    public ApacheHttpClientTransport(ApacheHttpTransportConfig config) {
        this(config, null);
    }

    public ApacheHttpClientTransport(ApacheHttpTransportConfig config, SSLContext sslContext) {
        this.config = config;

        var h2Config = H2Config.custom()
                .setPushEnabled(false)
                .setMaxConcurrentStreams(config.h2StreamsPerConnection())
                .setInitialWindowSize(16 * 1024 * 1024)
                .build();
        var ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(config.ioThreads())
                .setSoTimeout(Timeout.ofSeconds(30))
                .setTcpNoDelay(true)
                .build();

        var tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        if (sslContext != null) {
            tlsStrategyBuilder.setSslContext(sslContext);
        }

        var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsStrategy(tlsStrategyBuilder.build())
                .setMaxConnTotal(config.maxConnectionsPerHost())
                .setMaxConnPerRoute(config.maxConnectionsPerHost())
                .build();

        this.client = HttpAsyncClients.custom()
                .setVersionPolicy(toVersionPolicy(config.httpVersion()))
                .setH2Config(h2Config)
                .setConnectionManager(connectionManager)
                .setIOReactorConfig(ioReactorConfig)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build();
        this.client.start();
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        try {
            var consumer = new ApacheStreamingResponseConsumer(config.readBufferSize());
            client.execute(ApacheRequestProducerFactory.create(request), consumer, null);
            return awaitResponse(consumer, context.get(HttpContext.HTTP_REQUEST_TIMEOUT), config.requestTimeout());
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    private HttpResponse awaitResponse(
            ApacheStreamingResponseConsumer consumer,
            Duration contextTimeout,
            Duration defaultTimeout
    ) throws Exception {
        try {
            if (contextTimeout != null && !contextTimeout.isZero() && !contextTimeout.isNegative()) {
                return consumer.responseFuture().get(contextTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (defaultTimeout != null && !defaultTimeout.isZero() && !defaultTimeout.isNegative()) {
                return consumer.responseFuture().get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            return consumer.responseFuture().get();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static HttpVersionPolicy toVersionPolicy(HttpVersion version) {
        if (version == null) {
            return HttpVersionPolicy.NEGOTIATE;
        }
        return switch (version) {
            case HTTP_2 -> HttpVersionPolicy.FORCE_HTTP_2;
            case HTTP_1_0, HTTP_1_1 -> HttpVersionPolicy.FORCE_HTTP_1;
        };
    }

    @Override
    public void close() throws IOException {
        try {
            client.close();
        } catch (java.nio.channels.CancelledKeyException ignored) {}
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-apache";
        }

        @Override
        public ApacheHttpClientTransport createTransport(Document node, Document pluginSettings) {
            var config = new ApacheHttpTransportConfig().fromDocument(pluginSettings.asStringMap()
                    .getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);
            return new ApacheHttpClientTransport(config);
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
