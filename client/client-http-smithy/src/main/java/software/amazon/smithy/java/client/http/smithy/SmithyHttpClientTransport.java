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
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.io.datastream.DataStream;

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
            var options = RequestOptions.builder()
                    .requestTimeout(context.get(HttpContext.HTTP_REQUEST_TIMEOUT))
                    .build();
            HttpResponse response = client.send(request, options);
            // Eagerly drain and close the response body so the connection returns to the pool.
            // The SDK pipeline doesn't always read the body (e.g. PutObject responses are empty),
            // and even when it does read via asByteBuffer the close-on-consume path goes through
            // a wrapping DataStream that swallows close. Buffering here guarantees release at the
            // cost of an extra byte[] copy; acceptable for non-streaming RPC bodies.
            DataStream original = response.body();
            if (original != null) {
                try {
                    var buf = original.asByteBuffer();
                    byte[] bytes;
                    if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0
                            && buf.remaining() == buf.array().length) {
                        bytes = buf.array();
                    } else {
                        bytes = new byte[buf.remaining()];
                        buf.duplicate().get(bytes);
                    }
                    var buffered = DataStream.ofBytes(bytes, original.contentType());
                    return HttpResponse.of(response.httpVersion(), response.statusCode(),
                            response.headers(), buffered);
                } finally {
                    try {
                        original.close();
                    } catch (Exception ignored) {}
                }
            }
            return response;
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
            var poolBuilder = HttpConnectionPool.builder();

            if (config.requestTimeout() != null) {
                builder.requestTimeout(config.requestTimeout());
            }
            if (config.maxConnections() != null) {
                poolBuilder.maxTotalConnections(config.maxConnections());
                poolBuilder.maxConnectionsPerRoute(config.maxConnections());
            }
            if (config.h2StreamsPerConnection() != null) {
                poolBuilder.h2StreamsPerConnection(config.h2StreamsPerConnection());
            }
            if (config.h2InitialWindowSize() != null) {
                poolBuilder.h2InitialWindowSize(config.h2InitialWindowSize());
            }
            if (config.connectTimeout() != null) {
                poolBuilder.connectTimeout(config.connectTimeout());
            }
            if (config.maxIdleTime() != null) {
                poolBuilder.maxIdleTime(config.maxIdleTime());
            }
            if (config.httpVersionPolicy() != null) {
                poolBuilder.httpVersionPolicy(config.httpVersionPolicy());
            }

            builder.connectionPool(poolBuilder.build());

            return new SmithyHttpClientTransport(builder.build());
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
