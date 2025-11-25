/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

public final class HttpMcpProxy extends McpServerProxy {
    private static final InternalLogger LOG = InternalLogger.getLogger(HttpMcpProxy.class);
    private static final JsonCodec JSON_CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private final ClientTransport<HttpRequest, HttpResponse> transport;
    private final URI endpoint;
    private final String name;
    private final Signer<HttpRequest, ?> signer;
    private final Duration timeout;

    private HttpMcpProxy(Builder builder) {
        this.transport = builder.transport != null ? builder.transport : new JavaHttpClientTransport();
        this.endpoint = URI.create(builder.url);
        this.name = builder.name != null ? builder.name : sanitizeName(endpoint.getHost());
        this.signer = builder.signer;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(60);
    }

    private static String sanitizeName(String host) {
        if (host == null) {
            return "http-proxy-mcp";
        }
        return host.replaceAll("[^a-zA-Z0-9-]", "-");
    }

    public static class Builder {
        private String url;
        private String name;
        private Signer<HttpRequest, ?> signer;
        private ClientTransport<HttpRequest, HttpResponse> transport;
        private Duration timeout;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder signer(Signer<HttpRequest, ?> signer) {
            this.signer = signer;
            return this;
        }

        public Builder transport(ClientTransport<HttpRequest, HttpResponse> transport) {
            this.transport = transport;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpMcpProxy build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL must be provided");
            }
            return new HttpMcpProxy(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (request == null) {
                    throw new IllegalArgumentException("JsonRpcRequest cannot be null");
                }

                byte[] body = JSON_CODEC.serializeToString(request).getBytes(StandardCharsets.UTF_8);
                LOG.trace("Sending HTTP request to {}", endpoint);

                HttpRequest httpRequest = HttpRequest.builder()
                        .uri(endpoint)
                        .method("POST")
                        .withAddedHeader("Content-Type", "application/json")
                        .withAddedHeader("Accept", "application/json, text/event-stream")
                        .body(DataStream.ofBytes(body, "application/json"))
                        .build();

                if (signer != null) {
                    httpRequest = signer.sign(httpRequest, null, Context.create());
                }

                Context context = Context.create();
                context.put(HttpContext.HTTP_REQUEST_TIMEOUT, timeout);

                HttpResponse response = transport.send(context, httpRequest);
                LOG.trace("Received HTTP response with status: {}", response.statusCode());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    ByteBuffer bodyBuffer = response.body().asByteBuffer();
                    byte[] bodyBytes = new byte[bodyBuffer.remaining()];
                    bodyBuffer.get(bodyBytes);

                    if (bodyBytes.length > 0) {
                        var contentTypeHeader = response.headers().allValues("Content-Type");
                        String contentType = !contentTypeHeader.isEmpty() ? contentTypeHeader.get(0) : "";

                        if (contentType.contains("application/json")) {
                            try {
                                bodyBuffer.rewind();
                                return JsonRpcResponse.builder()
                                        .deserialize(JSON_CODEC.createDeserializer(bodyBuffer))
                                        .build();
                            } catch (Exception e) {
                                LOG.warn("Failed to deserialize JSON error response", e);
                            }
                        }
                    }

                    String errorBody = bodyBytes.length > 0
                            ? new String(bodyBytes, 0, Math.min(200, bodyBytes.length), StandardCharsets.UTF_8)
                            : "";
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .error(JsonRpcErrorResponse.builder()
                                    .code(response.statusCode())
                                    .message("HTTP " + response.statusCode() + ": " + errorBody)
                                    .build())
                            .build();
                }

                return JsonRpcResponse.builder()
                        .deserialize(JSON_CODEC.createDeserializer(response.body().asByteBuffer()))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void start() {
        // HTTP is connectionless, nothing to start
        LOG.debug("HTTP MCP proxy started for endpoint: {}", endpoint);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // HTTP client doesn't need explicit shutdown
        LOG.debug("HTTP MCP proxy shutdown for endpoint: {}", endpoint);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String name() {
        return name;
    }
}
