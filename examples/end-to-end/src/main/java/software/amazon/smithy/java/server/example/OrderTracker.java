/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.example.etoe.model.OrderStatus;

/**
 * This class is a stand-in for a database.
 */
final class OrderTracker {
    private static final Map<UUID, Order> ORDERS = new ConcurrentHashMap<>();

    private OrderTracker() {}

    public static void putOrder(Order order) {
        ORDERS.put(order.id(), order);
    }

    public static void completeOrder(String id) {
        ORDERS.computeIfPresent(UUID.fromString(id), (k,v) -> v.complete());
    }

    public static Order getOrderById(UUID id) {
        return ORDERS.get(id);
    }
}
