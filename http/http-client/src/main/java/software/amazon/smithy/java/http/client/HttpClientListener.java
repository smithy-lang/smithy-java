/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.net.InetAddress;
import java.util.List;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.CloseReason;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;

/**
 * Listener for HTTP client lifecycle events.
 *
 * <p>Listener methods are invoked synchronously on the thread performing the work. Implementations should avoid
 * blocking and keep allocation low.
 *
 * <h2>Unclosed response bodies and the leak signal</h2>
 * <p>For a successful response, {@link #onRequestEnd} fires when the caller closes, discards, or fully consumes the
 * response body. If the caller drops the response without doing any of these, the underlying connection is leaked
 * (never released to the pool) and <strong>{@code onRequestEnd} is intentionally never fired</strong>. A request with
 * an {@link #onRequestStart} and no matching {@code onRequestEnd} is therefore the canonical signal that a response
 * body was leaked — the missing event is not a defect, it is how a leak is detected. The client does not fire a late
 * {@code onRequestEnd} from a finalizer/{@link java.lang.ref.Cleaner}, because doing so would report a leak as a
 * normal (and wildly mis-timed) completion and corrupt any duration metric.
 *
 * <p>Consequently, a listener that opens per-exchange state on {@code onRequestStart} (a span, an in-flight gauge, a
 * timer) and only releases it on {@code onRequestEnd} will itself leak that state when a caller abandons a response.
 * Such listeners must bound their own state independently (a TTL, a max-in-flight cap, or periodic sweeping); the
 * framework does not guarantee a terminal event for an abandoned response.
 */
public interface HttpClientListener {
    /**
     * Called before a request starts.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param request request being sent
     */
    default void onRequestStart(long exchangeId, HttpRequest request) {}

    /**
     * Called when a request reaches terminal disposition.
     *
     * <p>For streaming responses, this fires when the response body is closed, discarded, fully consumed, or errors.
     * For failures before a response body exists, this fires when the failure is observed. It fires at most once per
     * exchange.
     *
     * <p>This event is <strong>not</strong> guaranteed: if the caller abandons a successful response without closing
     * or consuming its body, the connection leaks and this event never fires. See the class documentation for how
     * that absence serves as the leak signal.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param error failure, or null on success
     */
    default void onRequestEnd(long exchangeId, Throwable error) {}

    /**
     * Called before resolving a hostname.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param host hostname to resolve
     */
    default void onDnsStart(long exchangeId, String host) {}

    /**
     * Called after DNS resolution completes or fails.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param host hostname resolved
     * @param addresses resolved addresses, never null; empty when resolution failed before producing results
     * @param error failure, or null on success
     */
    default void onDnsEnd(long exchangeId, String host, List<InetAddress> addresses, Throwable error) {}

    /**
     * Called before opening a TCP connection.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route route being connected
     * @param address address being connected
     */
    default void onConnectStart(long exchangeId, Route route, InetAddress address) {}

    /**
     * Called after a TCP connection succeeds or fails.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route route being connected
     * @param address address being connected
     * @param error failure, or null on success
     */
    default void onConnectEnd(long exchangeId, Route route, InetAddress address, Throwable error) {}

    /**
     * Called before TLS negotiation with the origin server starts.
     *
     * <p>For requests through an HTTPS proxy, this event does not cover the client-to-proxy TLS hop; it
     * only covers origin (target) TLS, if any.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route route being negotiated
     */
    default void onTlsStart(long exchangeId, Route route) {}

    /**
     * Called after TLS negotiation with the origin server succeeds or fails.
     *
     * <p>For requests through an HTTPS proxy, this event does not cover the client-to-proxy TLS hop; it
     * only covers origin (target) TLS, if any.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route route being negotiated
     * @param protocol ALPN protocol, or null when none was negotiated
     * @param cipherSuite TLS cipher suite, or null when unavailable
     * @param error failure, or null on success
     */
    default void onTlsEnd(long exchangeId, Route route, String protocol, String cipherSuite, Throwable error) {}

    /**
     * Called before sending an HTTP CONNECT request to a proxy.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route target route
     * @param proxy proxy configuration
     * @param address proxy address being used
     */
    default void onProxyConnectStart(long exchangeId, Route route, ProxyConfiguration proxy, InetAddress address) {}

    /**
     * Called after an HTTP CONNECT request to a proxy completes or fails.
     *
     * <p>A CONNECT that returns a non-2xx status is a tunnel failure: {@code error} is non-null and
     * {@code statusCode} carries the rejected status. {@code error} is null only when the tunnel was
     * established successfully.
     *
     * @param exchangeId opaque client-generated exchange id
     * @param route target route
     * @param proxy proxy configuration
     * @param address proxy address being used
     * @param statusCode proxy response status, or -1 if no response was received
     * @param error failure, or null on success
     */
    default void onProxyConnectEnd(
            long exchangeId,
            Route route,
            ProxyConfiguration proxy,
            InetAddress address,
            int statusCode,
            Throwable error
    ) {}

    /**
     * Called after a connection is established and assigned to the pool.
     *
     * @param connection established connection
     */
    default void onConnectionCreated(HttpConnection connection) {}

    /**
     * Called when a connection is acquired.
     *
     * @param connection acquired connection
     * @param reused true if the connection was reused
     */
    default void onConnectionAcquired(HttpConnection connection, boolean reused) {}

    /**
     * Called when a connection is returned to the pool.
     *
     * @param connection returned connection
     */
    default void onConnectionReturned(HttpConnection connection) {}

    /**
     * Called when a connection is closed by the pool.
     *
     * @param connection closed connection
     * @param reason close reason
     */
    default void onConnectionClosed(HttpConnection connection, CloseReason reason) {}
}
