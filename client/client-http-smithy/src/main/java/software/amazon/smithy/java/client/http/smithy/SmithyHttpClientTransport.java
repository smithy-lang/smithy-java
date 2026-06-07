/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.smithy;

import java.io.IOException;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.RequestOptions;

/**
 * A client transport using Smithy's native blocking HTTP client with full HTTP/2 bidirectional streaming.
 *
 * <p>Unlike the JDK-based transport, this transport supports true bidirectional streaming over HTTP/2:
 * the request body can be written concurrently with reading the response body. For HTTP/1.1, the request
 * body is fully sent before the response is returned.
 */
public final class SmithyHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private final HttpClient client;

    /**
     * Create a transport with default settings.
     */
    public SmithyHttpClientTransport() {
        this(HttpClient.builder().build());
    }

    /**
     * Create a transport with the given HTTP client.
     *
     * @param client the Smithy HTTP client to use
     */
    public SmithyHttpClientTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        try {
            var options = new RequestOptions(context.get(HttpContext.HTTP_REQUEST_TIMEOUT));
            return client.send(request, options);
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-smithy";
        }

        @Override
        public SmithyHttpClientTransport createTransport(Document node, Document pluginSettings) {
            var config = new SmithyHttpTransportConfig().fromDocument(pluginSettings.asStringMap()
                    .getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);

            var builder = HttpClient.builder();

            if (config.requestTimeout() != null) {
                builder.requestTimeout(config.requestTimeout());
            }
            if (config.maxConnections() != null) {
                builder.maxTotalConnections(config.maxConnections());
                // If maxConnectionsPerRoute is not explicitly set, default it to maxConnections
                // for back-compat with prior behavior.
                if (config.maxConnectionsPerRoute() == null) {
                    builder.maxConnectionsPerRoute(config.maxConnections());
                }
            }
            if (config.maxConnectionsPerRoute() != null) {
                builder.maxConnectionsPerRoute(config.maxConnectionsPerRoute());
            }
            if (config.socketReceiveBufferSize() != null) {
                builder.socketReceiveBufferSize(config.socketReceiveBufferSize());
            }
            if (config.socketSendBufferSize() != null) {
                builder.socketSendBufferSize(config.socketSendBufferSize());
            }
            if (config.h2StreamsPerConnection() != null) {
                builder.h2StreamsPerConnection(config.h2StreamsPerConnection());
            }
            if (config.h2InitialWindowSize() != null) {
                builder.h2InitialWindowSize(config.h2InitialWindowSize());
            }
            if (config.connectTimeout() != null) {
                builder.connectTimeout(config.connectTimeout());
            }
            if (config.maxIdleTime() != null) {
                builder.maxIdleTime(config.maxIdleTime());
            }
            if (config.httpVersionPolicy() != null) {
                builder.httpVersionPolicy(config.httpVersionPolicy());
            }

            return new SmithyHttpClientTransport(builder.build());
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
