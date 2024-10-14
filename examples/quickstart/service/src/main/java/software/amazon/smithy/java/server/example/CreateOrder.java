/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.UUID;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.example.model.CreateOrderInput;
import software.amazon.smithy.java.server.example.model.CreateOrderOutput;
import software.amazon.smithy.java.server.example.model.OrderStatus;
import software.amazon.smithy.java.server.example.service.CreateOrderOperation;

/**
 * Create an order for a coffee.
 */
final class CreateOrder implements CreateOrderOperation {
    @Override
    public CreateOrderOutput createOrder(CreateOrderInput input, RequestContext context) {
        var id = UUID.randomUUID();

        OrderTracker.putOrder(new Order(id, input.coffeeType(), OrderStatus.IN_PROGRESS));

        return CreateOrderOutput.builder()
            .id(id.toString())
            .coffeeType(input.coffeeType())
            .status(OrderStatus.IN_PROGRESS)
            .build();
    }
}
