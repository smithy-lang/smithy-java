/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * Essentially a very fast custom hashmap of stream ID to H2Exchange.
 *
 * <p><b>Architecture:</b>
 * <ul>
 * <li><b>L1 Cache (Array):</b> A fast, direct-mapped AtomicReferenceArray. 99% of streams live here.
 * <li><b>L2 Storage (Map):</b> A ConcurrentHashMap spillover. If the array slot for a new stream is already
 * occupied (by a long-lived stream), the new stream goes here.</li>
 * </ul>
 *
 * <p>This guarantees that "Lapping" (ID wrap-around) never causes data loss, even if a stream stays open indefinitely
 * while millions of others cycle through.
 *
 * <p>HTTP/2 client stream IDs have useful properties we exploit:
 * <ul>
 *   <li>Always odd numbers: 1, 3, 5, 7, ...</li>
 *   <li>Monotonically increasing (never reused on same connection)</li>
 * </ul>
 *
 * <p>We map stream IDs to array slots via: {@code slot = ((streamId - 1) >>> 1) & slotMask}. This gives O(1) lookup
 * without hashing or Integer boxing overhead.
 */
final class StreamRegistry {

    // 4096 slots covers normal concurrency (100-1000) with ample headroom.
    // Memory cost: 4096 * 4 bytes (ref) = 16KB per connection.
    private static final int SLOTS = 4096;
    private static final int SLOT_MASK = SLOTS - 1;

    private final AtomicReferenceArray<H2Exchange> fastPath = new AtomicReferenceArray<>(SLOTS);
    private final ConcurrentHashMap<Integer, H2Exchange> spillover = new ConcurrentHashMap<>();

    /**
     * Map stream ID to slot index.
     * Stream IDs are odd (1, 3, 5, ...), so we divide by 2 to get compact indices.
     */
    private static int streamIdToSlot(int streamId) {
        return ((streamId - 1) >>> 1) & SLOT_MASK;
    }

    /**
     * Register a new exchange.
     *
     * <p>If the array slot is empty, the exchange goes there (fast path). If the slot is occupied by a long-lived
     * stream, the new exchange spills over to the ConcurrentHashMap as a safety net.
     *
     * @param streamId the stream ID
     * @param exchange the exchange to register
     */
    void put(int streamId, H2Exchange exchange) {
        int slot = streamIdToSlot(streamId);

        // Optimistic: Try to put in the fast array
        H2Exchange existing = fastPath.get(slot);

        if (existing == null) {
            // Slot is empty, claim it.
            fastPath.set(slot, exchange);
        } else {
            // Collision: the slot is taken by an older, long-lived stream. Don't overwrite it, rather spill over.
            spillover.put(streamId, exchange);
        }
    }

    /**
     * Get an exchange by stream ID.
     *
     * <p>First checks the fast array path, then falls back to the spillover map if there's a stream ID mismatch
     * (indicating the stream was spilled).
     *
     * @param streamId the stream ID
     * @return the exchange, or null if not found
     */
    H2Exchange get(int streamId) {
        int slot = streamIdToSlot(streamId);
        H2Exchange exchange = fastPath.get(slot);
        return exchange != null && exchange.getStreamId() == streamId ? exchange : spillover.get(streamId);
    }

    /**
     * Remove an exchange from the registry.
     *
     * @param streamId the stream ID
     * @return true if the exchange was removed, false if not found
     */
    boolean remove(int streamId) {
        int slot = streamIdToSlot(streamId);
        H2Exchange exchange = fastPath.get(slot);

        // Check Fast Path
        if (exchange != null && exchange.getStreamId() == streamId) {
            // CAS ensures we don't delete a NEW stream that just claimed the slot
            return fastPath.compareAndSet(slot, exchange, null);
        }

        // Check Slow Path
        return spillover.remove(streamId) != null;
    }

    /**
     * Iterate over all active exchanges.
     * Used for cold paths (cleanup, settings changes, connection close).
     *
     * @param action the action to perform on each exchange
     */
    void forEach(Consumer<H2Exchange> action) {
        // Iterate Array and the spillover map
        for (int i = 0; i < SLOTS; i++) {
            H2Exchange exchange = fastPath.get(i);
            if (exchange != null) {
                action.accept(exchange);
            }
        }

        if (!spillover.isEmpty()) {
            spillover.values().forEach(action);
        }
    }

    /**
     * Iterate over exchanges matching a predicate.
     *
     * @param predicate condition to check
     * @param action the action to perform on matching exchanges
     */
    void forEachMatching(java.util.function.IntPredicate predicate, Consumer<H2Exchange> action) {
        // Iterate Array and spillover map.
        for (int i = 0; i < SLOTS; i++) {
            H2Exchange exchange = fastPath.get(i);
            if (exchange != null && predicate.test(exchange.getStreamId())) {
                action.accept(exchange);
            }
        }

        if (!spillover.isEmpty()) {
            for (H2Exchange exchange : spillover.values()) {
                if (predicate.test(exchange.getStreamId())) {
                    action.accept(exchange);
                }
            }
        }
    }

    /**
     * Clear all slots and close exchanges.
     *
     * @param closeAction action to run on each exchange during clear
     */
    void clearAndClose(Consumer<H2Exchange> closeAction) {
        for (int i = 0; i < SLOTS; i++) {
            H2Exchange exchange = fastPath.getAndSet(i, null);
            if (exchange != null) {
                closeAction.accept(exchange);
            }
        }

        if (!spillover.isEmpty()) {
            for (H2Exchange exchange : spillover.values()) {
                closeAction.accept(exchange);
            }
            spillover.clear();
        }
    }
}
