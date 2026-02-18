/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.smithy;

import java.io.IOException;
import java.io.OutputStream;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.RequestOptions;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A client transport using Smithy's native blocking HTTP client with full HTTP/2 bidirectional streaming.
 *
 * <p>Unlike the JDK-based transport, this transport supports true bidirectional streaming over HTTP/2:
 * the request body can be written concurrently with reading the response body. For HTTP/1.1, the request
 * body is fully sent before the response is returned.
 */
public final class SmithyHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SmithyHttpClientTransport.class);

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
            return doSend(context, request);
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    private HttpResponse doSend(Context context, HttpRequest request) throws Exception {
        var options = RequestOptions.builder()
                .requestTimeout(context.get(HttpContext.HTTP_REQUEST_TIMEOUT))
                .build();
        HttpExchange exchange = client.newExchange(request, options);

        try {
            DataStream requestBody = request.body();
            boolean hasBody = requestBody != null && requestBody.contentLength() != 0;
            if (!hasBody) {
                // Close body right away.
                exchange.requestBody().close();
            } else if (exchange.supportsBidirectionalStreaming()) {
                // H2: write body on a virtual thread so response can stream back concurrently (bidi streaming)
                Thread.startVirtualThread(() -> {
                    try (OutputStream out = exchange.requestBody()) {
                        requestBody.writeTo(out);
                    } catch (IOException e) {
                        LOGGER.debug("Error writing request body: {}", e.getMessage());
                    }
                });
            } else {
                // H1: write body inline. It must complete before response is available.
                try (OutputStream out = exchange.requestBody()) {
                    requestBody.writeTo(out);
                }
            }

            return buildResponse(exchange);
        } catch (Exception e) {
            exchange.close();
            throw e;
        }
    }

    private HttpResponse buildResponse(HttpExchange exchange) throws IOException {
        int statusCode = exchange.responseStatusCode();
        HttpHeaders headers = exchange.responseHeaders();

        var length = headers.contentLength();
        long adaptedLength = length == null ? -1 : length;
        var contentType = headers.contentType();

        // Wrap the response body stream as a DataStream.
        // The exchange auto-closes when both request and response streams are closed.
        var body = DataStream.ofInputStream(exchange.responseBody(), contentType, adaptedLength);

        return HttpResponse.builder()
                .httpVersion(exchange.request().httpVersion())
                .statusCode(statusCode)
                .headers(headers)
                .body(body)
                .build();
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
        public SmithyHttpClientTransport createTransport(Document node) {
            var config = new SmithyHttpTransportConfig().fromDocument(node);

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
