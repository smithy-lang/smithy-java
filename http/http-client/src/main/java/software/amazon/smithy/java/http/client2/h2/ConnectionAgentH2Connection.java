/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngine;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.MultiplexedHttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import java.net.Socket;

/**
 * Experimental HTTP/2 cleartext connection backed by the connection-agent transport.
 *
 * <p>This is the production-facing adapter around {@link ConnectionAgentH2cTransport}. It exposes the
 * standard {@link HttpConnection} / {@link HttpExchange} interfaces while preserving the single-owner
 * connection model internally.
 */
public final class ConnectionAgentH2Connection implements MultiplexedHttpConnection {

    private interface Backend extends AutoCloseable {
        HttpResponse send(HttpRequest request) throws IOException;

        void setStreamReleaseCallback(Runnable callback);

        boolean canAcceptMoreStreams();

        int getActiveStreamCountIfAccepting();

        long getIdleTimeNanos();

        boolean isActive();

        SSLSession sslSession();

        String negotiatedProtocol();

        Object getStats();

        @Override
        void close() throws IOException;
    }

    private static final class H2cBackend implements Backend {
        private final ConnectionAgentH2cTransport transport;

        private H2cBackend(ConnectionAgentH2cTransport transport) {
            this.transport = transport;
        }

        @Override
        public HttpResponse send(HttpRequest request) throws IOException {
            return transport.send(request);
        }

        @Override
        public void setStreamReleaseCallback(Runnable callback) {
            transport.setStreamReleaseCallback(callback);
        }

        @Override
        public boolean canAcceptMoreStreams() {
            return transport.canAcceptMoreStreams();
        }

        @Override
        public int getActiveStreamCountIfAccepting() {
            return transport.getActiveStreamCountIfAccepting();
        }

        @Override
        public long getIdleTimeNanos() {
            return transport.getIdleTimeNanos();
        }

        @Override
        public boolean isActive() {
            return transport.isActive();
        }

        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public String negotiatedProtocol() {
            return "h2c";
        }

        @Override
        public Object getStats() {
            return null;
        }

        @Override
        public void close() {
            transport.close();
        }
    }

    private static final class H2Backend implements Backend {
        private final ConnectionAgentH2Transport transport;

        private H2Backend(ConnectionAgentH2Transport transport) {
            this.transport = transport;
        }

        @Override
        public HttpResponse send(HttpRequest request) throws IOException {
            return transport.send(request);
        }

        @Override
        public void setStreamReleaseCallback(Runnable callback) {
            transport.setStreamReleaseCallback(callback);
        }

        @Override
        public boolean canAcceptMoreStreams() {
            return transport.canAcceptMoreStreams();
        }

        @Override
        public int getActiveStreamCountIfAccepting() {
            return transport.getActiveStreamCountIfAccepting();
        }

        @Override
        public long getIdleTimeNanos() {
            return transport.getIdleTimeNanos();
        }

        @Override
        public boolean isActive() {
            return transport.isActive();
        }

        @Override
        public SSLSession sslSession() {
            return transport.sslSession();
        }

        @Override
        public String negotiatedProtocol() {
            return transport.negotiatedProtocol();
        }

        @Override
        public Object getStats() {
            return transport.getStats();
        }

        @Override
        public void close() throws IOException {
            transport.close();
        }
    }

    private final Backend transport;
    private final Route route;
    private volatile boolean closed;

    public ConnectionAgentH2Connection(Route route) throws IOException {
        if (route.isSecure()) {
            throw new IllegalArgumentException("ConnectionAgentH2Connection only supports cleartext routes: " + route);
        }
        try {
            this.transport = new H2cBackend(new ConnectionAgentH2cTransport(route));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to create connection-agent H2 connection for " + route, e);
        }
        this.route = route;
    }

    public ConnectionAgentH2Connection(Route route, Socket socket, SSLEngine engine) throws IOException {
        if (!route.isSecure()) {
            throw new IllegalArgumentException("Secure transport constructor requires TLS route: " + route);
        }
        try {
            this.transport = new H2Backend(new ConnectionAgentH2Transport(route, socket, engine));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to create TLS connection-agent H2 connection for " + route, e);
        }
        this.route = route;
    }

    @Override
    public HttpExchange newExchange(HttpRequest request) throws IOException {
        if (closed || !transport.isActive()) {
            throw new IOException("Connection is closed");
        }
        return new ConnectionAgentH2Exchange(this, request);
    }

    HttpResponse send(HttpRequest request) throws IOException {
        return transport.send(request);
    }

    @Override
    public void setStreamReleaseCallback(Runnable callback) {
        transport.setStreamReleaseCallback(callback);
    }

    @Override
    public boolean canAcceptMoreStreams() {
        return !closed && transport.canAcceptMoreStreams();
    }

    @Override
    public int getActiveStreamCountIfAccepting() {
        return closed ? -1 : transport.getActiveStreamCountIfAccepting();
    }

    @Override
    public long getIdleTimeNanos() {
        return closed ? 0 : transport.getIdleTimeNanos();
    }

    @Override
    public HttpVersion httpVersion() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public Route route() {
        return route;
    }

    @Override
    public SSLSession sslSession() {
        return transport.sslSession();
    }

    @Override
    public String negotiatedProtocol() {
        return transport.negotiatedProtocol();
    }

    @Override
    public boolean isActive() {
        return !closed && transport.isActive();
    }

    Object getStats() {
        return transport.getStats();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        transport.close();
    }
}
