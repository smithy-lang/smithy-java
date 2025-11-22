/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A connection pool listener that detects connection leaks.
 *
 * <p>This listener tracks acquired connections using weak references. If a connection is garbage collected without
 * being released or evicted, it indicates a leak - the caller forgot to return the connection to the pool.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default: logs warnings, checks every 30 seconds
 * LeakDetectingListener leakDetector = new LeakDetectingListener();
 *
 * // Custom callback
 * LeakDetectingListener leakDetector = new LeakDetectingListener(
 *     route -> log.warn("Connection leak detected for route: {}", route)
 * );
 *
 * HttpConnectionPool pool = HttpConnectionPool.builder()
 *     .addListener(leakDetector)
 *     .build();
 * }</pre>
 *
 * <h2>(current) Limitations</h2>
 * <ul>
 *   <li>Leaks are only detected after GC runs - there may be a delay</li>
 *   <li>No stack traces are captured (for performance) - you know a leak occurred but not where</li>
 *   <li>HTTP/2 connections may appear as leaks if streams are not properly closed, since the connection stays
 *   acquired while streams are active</li>
 * </ul>
 *
 * @see ConnectionPoolListener
 */
public final class LeakDetectingListener implements ConnectionPoolListener {
    // The detection relies on the JVM's garbage collector: when a weakly-referenced
    // connection is collected, its reference is enqueued in a {@link ReferenceQueue}.
    // A background thread periodically polls this queue to discover leaked connections.

    private static final InternalLogger LOGGER = InternalLogger.getLogger(LeakDetectingListener.class);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(30);

    private final Set<TrackedConnection> tracked = ConcurrentHashMap.newKeySet();
    private final ReferenceQueue<HttpConnection> queue = new ReferenceQueue<>();
    private final Consumer<Route> onLeakDetected;

    /**
     * Create a leak detecting listener that logs when a leak is detected.
     *
     * <p>Checks for leaks every 30 seconds using a daemon virtual thread.
     */
    public LeakDetectingListener() {
        this(r -> LOGGER.warn("Connection leak detected for route: {}", r));
    }

    /**
     * Create a leak detecting listener with a callback for detected leaks.
     *
     * <p>Checks for leaks every 30 seconds using a daemon virtual thread.
     *
     * @param onLeakDetected callback invoked for each leaked connection, receives the route
     */
    public LeakDetectingListener(Consumer<Route> onLeakDetected) {
        this(onLeakDetected, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Create a leak detecting listener with a callback and custom check interval.
     *
     * @param onLeakDetected callback invoked for each leaked connection, receives the route
     * @param checkInterval how often to check for leaks
     */
    public LeakDetectingListener(Consumer<Route> onLeakDetected, Duration checkInterval) {
        this.onLeakDetected = Objects.requireNonNull(onLeakDetected, "onLeakDetected");
        Objects.requireNonNull(checkInterval, "checkInterval");

        // Start background thread to check for leaks periodically
        Thread.ofVirtual().name("leak-detector").start(() -> {
            while (true) {
                try {
                    Thread.sleep(checkInterval);
                    checkForLeaks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void onAcquire(HttpConnection connection, boolean reused) {
        tracked.add(new TrackedConnection(connection, queue));
    }

    @Override
    public void onReturn(HttpConnection connection) {
        removeTracked(connection);
    }

    @Override
    public void onClosed(HttpConnection connection, CloseReason reason) {
        removeTracked(connection);
    }

    private void removeTracked(HttpConnection connection) {
        tracked.removeIf(t -> t.get() == connection);
    }

    private int checkForLeaks() {
        int leakCount = 0;
        TrackedConnection leaked;
        while ((leaked = (TrackedConnection) queue.poll()) != null) {
            tracked.remove(leaked);
            leakCount++;
            onLeakDetected.accept(leaked.route);
        }
        return leakCount;
    }

    private static final class TrackedConnection extends WeakReference<HttpConnection> {
        final Route route;

        TrackedConnection(HttpConnection connection, ReferenceQueue<HttpConnection> queue) {
            super(connection, queue);
            // Capture route now since connection will be GC'd when leak is detected
            this.route = connection.route();
        }
    }
}
