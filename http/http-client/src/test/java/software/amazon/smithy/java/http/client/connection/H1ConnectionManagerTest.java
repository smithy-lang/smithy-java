/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;

class H1ConnectionManagerTest {

    private static final Route TEST_ROUTE = Route.direct("http", "example.com", 80);
    private static final long MAX_IDLE_NANOS = TimeUnit.SECONDS.toNanos(30);

    @Test
    void tryAcquireReturnsNullWhenPoolEmpty() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);

        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNull(result, "Should return null when pool is empty");
    }

    @Test
    void tryAcquireReturnsPooledConnection() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var connection = new TestConnection();

        manager.release(TEST_ROUTE, connection, false);
        // Need to ensure pool exists first
        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);

        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNotNull(result, "Should return pooled connection");
        assertEquals(connection, result.connection(), "Should return the same connection");
    }

    @Test
    void tryAcquireRejectsOverlyIdleConnections() throws Exception {
        var manager = new H1ConnectionManager(TimeUnit.MILLISECONDS.toNanos(10)); // 10ms max idle
        var closeCalled = new AtomicBoolean(false);
        var connection = new TestConnection() {
            @Override
            public void close() {
                closeCalled.set(true);
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);

        Thread.sleep(50); // Wait longer than max idle time

        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNull(result, "Should not return overly idle connection");
        assertTrue(closeCalled.get(), "Overly idle connection should be closed");
    }

    @Test
    void tryAcquireValidatesConnectionIdleLongerThanThreshold() throws Exception {
        var manager = new H1ConnectionManager(TimeUnit.SECONDS.toNanos(30)); // 30s max idle
        var validateCalled = new AtomicBoolean(false);
        var connection = new TestConnection() {
            @Override
            public boolean validateForReuse() {
                validateCalled.set(true);
                return true;
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);

        Thread.sleep(1100); // Wait > 1 second (VALIDATION_THRESHOLD_NANOS)

        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNotNull(result, "Should return validated connection");
        assertTrue(validateCalled.get(), "validateForReuse should be called for connections idle > 1s");
    }

    @Test
    void tryAcquireSkipsInvalidConnections() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var invalidConnection = new TestConnection() {
            @Override
            public boolean isActive() {
                return false;
            }
        };
        var validConnection = new TestConnection();

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, validConnection, false);
        manager.release(TEST_ROUTE, invalidConnection, false);

        // Should skip invalid and return valid (LIFO order, so invalid is tried first)
        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNotNull(result, "Should return valid connection");
        assertEquals(validConnection, result.connection(), "Should skip invalid and return valid");
    }

    @Test
    void tryAcquireClosesInvalidConnections() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var closeCalled = new AtomicBoolean(false);
        var active = new AtomicBoolean(true);
        var invalidConnection = new TestConnection() {
            @Override
            public boolean isActive() {
                return active.get();
            }

            @Override
            public void close() {
                closeCalled.set(true);
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, invalidConnection, false);
        // Connection becomes inactive after being pooled
        active.set(false);

        manager.tryAcquire(TEST_ROUTE, 10);

        assertTrue(closeCalled.get(), "Invalid connection should be closed");
    }

    @Test
    void releaseReturnsFalseWhenConnectionInactive() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var inactiveConnection = new TestConnection() {
            @Override
            public boolean isActive() {
                return false;
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        boolean released = manager.release(TEST_ROUTE, inactiveConnection, false);

        assertFalse(released, "Should not release inactive connection");
    }

    @Test
    void releaseReturnsFalseWhenPoolClosed() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var connection = new TestConnection();

        manager.getOrCreatePool(TEST_ROUTE, 10);
        boolean released = manager.release(TEST_ROUTE, connection, true);

        assertFalse(released, "Should not release when pool is closed");
    }

    @Test
    void releaseReturnsFalseWhenNoPoolExists() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var connection = new TestConnection();

        boolean released = manager.release(TEST_ROUTE, connection, false);

        assertFalse(released, "Should not release when no pool exists");
    }

    @Test
    void removeRemovesConnectionFromPool() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var connection = new TestConnection();

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);
        manager.remove(TEST_ROUTE, connection);

        var result = manager.tryAcquire(TEST_ROUTE, 10);

        assertNull(result, "Connection should be removed from pool");
    }

    @Test
    void cleanupIdleRemovesExpiredConnections() throws Exception {
        var manager = new H1ConnectionManager(1); // 1 nanosecond max idle
        var connection = new TestConnection();

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);

        Thread.sleep(10); // Ensure connection is expired

        var removed = new AtomicInteger(0);
        int count = manager.cleanupIdle((conn, reason) -> removed.incrementAndGet());

        assertEquals(1, count, "Should remove 1 expired connection");
        assertEquals(1, removed.get(), "Callback should be called once");
    }

    @Test
    void cleanupIdleRemovesUnhealthyConnections() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var unhealthyConnection = new TestConnection() {
            private boolean active = true;

            @Override
            public boolean isActive() {
                return active;
            }

            void setInactive() {
                active = false;
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, unhealthyConnection, false);
        unhealthyConnection.setInactive();

        var reasons = new ArrayList<CloseReason>();
        manager.cleanupIdle((conn, reason) -> reasons.add(reason));

        assertEquals(1, reasons.size(), "Should remove unhealthy connection");
        assertEquals(CloseReason.UNEXPECTED_CLOSE, reasons.get(0), "Reason should be UNEXPECTED_CLOSE");
    }

    @Test
    void closeAllClosesAllConnections() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);
        var closedConnections = new AtomicInteger(0);
        var connection1 = new TestConnection() {
            @Override
            public void close() {
                closedConnections.incrementAndGet();
            }
        };
        var connection2 = new TestConnection() {
            @Override
            public void close() {
                closedConnections.incrementAndGet();
            }
        };

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection1, false);
        manager.release(TEST_ROUTE, connection2, false);

        var exceptions = new ArrayList<IOException>();
        manager.closeAll(exceptions, conn -> {});

        assertEquals(2, closedConnections.get(), "All connections should be closed");
        assertTrue(exceptions.isEmpty(), "No exceptions expected");
    }

    @Test
    void getOrCreatePoolThrowsOnInconsistentMaxConnections() {
        var manager = new H1ConnectionManager(MAX_IDLE_NANOS);

        manager.getOrCreatePool(TEST_ROUTE, 10);

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> manager.getOrCreatePool(TEST_ROUTE, 20));

        assertTrue(ex.getMessage().contains("maxConnections=10"));
        assertTrue(ex.getMessage().contains("cannot change to 20"));
    }

    @Test
    void cleanupIdleRemovesEmptyPools() {
        var manager = new H1ConnectionManager(1); // 1 nanosecond max idle
        var connection = new TestConnection();

        manager.getOrCreatePool(TEST_ROUTE, 10);
        manager.release(TEST_ROUTE, connection, false);

        // First cleanup removes the expired connection
        manager.cleanupIdle((conn, reason) -> {});

        // Pool should be removed since it's empty
        // Verify by checking that we can create a new pool with different maxConnections
        // (would throw if old pool still existed)
        manager.getOrCreatePool(TEST_ROUTE, 5); // Different maxConnections - should work
    }

    // Test connection implementation
    private static class TestConnection implements HttpConnection {
        @Override
        public HttpExchange newExchange(HttpRequest request) {
            return null;
        }

        @Override
        public HttpVersion httpVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Route route() {
            return TEST_ROUTE;
        }

        @Override
        public void close() {}

        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public String negotiatedProtocol() {
            return null;
        }

        @Override
        public boolean validateForReuse() {
            return true;
        }
    }
}
