/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.util.HashedWheelTimer;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * HTTP/1.1 driver for the virtual-thread-blocking transport: pools {@link VtH1Connection}s and runs
 * each request synchronously on the calling thread via {@link VtH1Exchange}, with a single
 * transparent retry on a fresh connection when a reused keep-alive turns out to have been closed
 * server-side before any response was received.
 */
final class VtH1Transport implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(VtH1Transport.class);

    static {
        // Netty defaults the buffer leak detector to SIMPLE, which captures a Throwable stack trace
        // for a sampled fraction of every buffer allocate/release. On this hot client path that
        // showed up as ~1% CPU in Throwable.fillInStackTrace with no diagnostic value. Disable it
        // unless the operator has explicitly chosen a level via either Netty system property.
        if (System.getProperty("io.netty.leakDetection.level") == null
                && System.getProperty("io.netty.leakDetectionLevel") == null) {
            ResourceLeakDetector.setLevel(io.netty.util.ResourceLeakDetector.Level.DISABLED);
        }
    }

    private final VtConnectionPool pool;
    private final VtTlsContext tlsContext;
    // Shared single-thread watchdog: enforces read deadlines for blocking SocketChannel reads (which
    // ignore SO_TIMEOUT). One timer for the whole transport — O(1) arm/cancel per read, far cheaper
    // than the per-read copies the direct read removes. 100ms tick is plenty for request timeouts.
    private final HashedWheelTimer readTimer;

    VtH1Transport(NettyHttpTransportConfig config) {
        this.tlsContext = VtTlsContext.create(
                config.preferOpenSsl(),
                config.trustAllCertificates(),
                List.of("http/1.1"));
        this.readTimer = new HashedWheelTimer(
                new DefaultThreadFactory("smithy-netty-vt-read-timeout", true),
                100,
                TimeUnit.MILLISECONDS);
        this.pool = new VtConnectionPool(config, tlsContext, readTimer);
    }

    VtTlsContext tlsContext() {
        return tlsContext;
    }

    HttpResponse send(Route route, HttpRequest request) throws IOException {
        try {
            return attempt(route, request, false);
        } catch (StaleConnectionException stale) {
            if (request.body() == null || request.body().isReplayable()) {
                LOGGER.debug("Retrying on a fresh connection after stale reuse to {}", route);
                return attempt(route, request, true);
            }
            throw stale;
        }
    }

    private HttpResponse attempt(Route route, HttpRequest request, boolean forceFresh) throws IOException {
        VtH1Connection conn = forceFresh ? pool.acquireFresh(route) : pool.acquire(route);
        boolean fromReuse = conn.isFromReuse();
        // The exchange invokes this exactly once when the response body is fully consumed/closed.
        var completed = new AtomicBoolean(false);
        try {
            return VtH1Exchange.execute(conn, request, reuse -> {
                if (completed.compareAndSet(false, true)) {
                    if (reuse) {
                        pool.release(conn);
                    } else {
                        pool.dispose(conn);
                    }
                }
            });
        } catch (IOException | RuntimeException e) {
            // The exchange failed before handing off the body lifecycle; dispose here.
            if (completed.compareAndSet(false, true)) {
                pool.dispose(conn);
            }
            // A reused connection that failed before any response is the classic stale keep-alive:
            // signal a one-shot retry on a fresh connection (caller gates on body replayability).
            if (fromReuse && e instanceof IOException io) {
                throw new StaleConnectionException("Reused H1 connection failed before response", io);
            }
            throw e;
        }
    }

    void evictIdle() {
        pool.evictIdle();
    }

    @Override
    public void close() {
        pool.close();
        readTimer.stop();
    }
}
