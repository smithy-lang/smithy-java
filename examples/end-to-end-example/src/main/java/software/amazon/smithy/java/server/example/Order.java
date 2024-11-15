/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.UUID;
import software.amazon.smithy.java.example.model.CoffeeType;
import software.amazon.smithy.java.example.model.OrderStatus;

/**
 * A coffee drink order.
 *
 * @param id UUID of the order
 * @param type Type of drink for the order
 * @param status status of the order.
 */
public record Order(UUID id, CoffeeType type, OrderStatus status) {
}
