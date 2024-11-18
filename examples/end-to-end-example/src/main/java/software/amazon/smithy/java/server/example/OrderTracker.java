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
import software.amazon.smithy.java.example.model.OrderStatus;

/**
 * This class is a stand-in for a database.
 */
final class OrderTracker {
    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private static final Map<UUID, Order> ORDERS = new ConcurrentHashMap<>();

    public static void putOrder(Order order) {
        ORDERS.put(order.id(), order);

        // Start a process to complete the order in the background.
        executor.schedule(
            () -> ORDERS.put(order.id(), new Order(order.id(), order.type(), OrderStatus.COMPLETED)),
            5,
            TimeUnit.SECONDS
        );
    }

    public static Order getOrderById(UUID id) {
        return ORDERS.get(id);
    }
}
