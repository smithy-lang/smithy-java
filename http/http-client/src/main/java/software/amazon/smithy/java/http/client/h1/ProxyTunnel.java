/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpCredentials;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.connection.Route;

/**
 * Establishes HTTP CONNECT tunnels through proxies.
 */
public final class ProxyTunnel {

    private ProxyTunnel() {}

    /**
     * Result of establishing a CONNECT tunnel through a proxy.
     *
     * @param socket the tunneled socket if successful, null if failed
     * @param statusCode HTTP status code from proxy
     * @param headers response headers from proxy
     */
    public record Result(Socket socket, int statusCode, HttpHeaders headers) {}

    /**
     * Connects to a proxy.
     *
     * <p>Performs the proxy handshake including authentication if credentials
     * are provided. Supports multi-round auth protocols (e.g., NTLM, Negotiate).
     *
     * @param proxySocket socket connected to proxy server
     * @param targetHost target host for CONNECT request
     * @param targetPort target port for CONNECT request
     * @param credentials optional credentials for proxy authentication
     * @param readTimeout timeout for read operations
     * @return tunnel result with socket (if successful) and response details
     * @throws IOException if I/O error occurs during tunnel establishment
     */
    public static Result establish(
            Socket proxySocket,
            String targetHost,
            int targetPort,
            HttpCredentials credentials,
            Duration readTimeout
    ) throws IOException {
        Route proxyRoute = Route.direct(
                "http",
                proxySocket.getInetAddress().getHostAddress(),
                proxySocket.getPort());
        H1Connection conn = new H1Connection(proxySocket, proxyRoute, readTimeout);

        HttpResponse priorResponse = null;

        do {
            HttpRequest.Builder requestBuilder = HttpRequest.builder()
                    .method("CONNECT")
                    .uri(URI.create("http://" + targetHost + ":" + targetPort))
                    .withAddedHeader("Host", targetHost + ":" + targetPort)
                    .withAddedHeader("Proxy-Connection", "Keep-Alive");

            if (credentials != null) {
                boolean applied = credentials.authenticate(requestBuilder, priorResponse);
                if (!applied && priorResponse != null) {
                    break;
                }
            }

            HttpExchange exchange = conn.newExchange(requestBuilder.build());
            exchange.requestBody().close();

            int status = exchange.responseStatusCode();
            HttpHeaders headers = exchange.responseHeaders();

            if (status == 200) {
                conn.releaseExchange();
                return new Result(proxySocket, status, headers);
            }

            priorResponse = HttpResponse.builder()
                    .statusCode(status)
                    .headers(headers)
                    .build();

            conn.releaseExchange();

        } while (priorResponse.statusCode() == 407 && credentials != null);

        return new Result(null, priorResponse.statusCode(), priorResponse.headers());
    }
}
