/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
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

    private final HttpClient client;
    private final URI endpoint;
    private final String name;
    private final Map<String, String> headers;

    private HttpMcpProxy(Builder builder) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.endpoint = URI.create(builder.url);
        this.name = builder.name != null ? builder.name : "HTTP-" + endpoint.getHost();
        this.headers = builder.headers != null ? Map.copyOf(builder.headers) : Map.of();
    }

    public static class Builder {
        private String url;
        private String name;
        private Map<String, String> headers;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
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
        try {
            if (request == null) {
                throw new IllegalArgumentException("JsonRpcRequest cannot be null");
            }
            String body = JSON_CODEC.serializeToString(request);
            LOG.debug("Sending HTTP request to {}", endpoint);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            // Add custom headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            HttpRequest httpRequest = requestBuilder.build();

            return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        LOG.debug("Received HTTP response with status: {}", response.statusCode());

                        if (response.statusCode() != 200) {
                            throw new RuntimeException("HTTP error " + response.statusCode() + ": " + response.body());
                        }

                        try {
                            return JsonRpcResponse.builder()
                                    .deserialize(JSON_CODEC.createDeserializer(
                                            response.body().getBytes(StandardCharsets.UTF_8)))
                                    .build();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse JSON-RPC response: " + e.getMessage(), e);
                        }
                    })
                    .exceptionally(throwable -> {
                        LOG.error("HTTP request failed", throwable);
                        throw new RuntimeException("HTTP request failed: " + throwable.getMessage(), throwable);
                    });
        } catch (Exception e) {
            LOG.error("Failed to serialize request", e);
            return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to serialize request: " + e.getMessage(), e));
        }
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
