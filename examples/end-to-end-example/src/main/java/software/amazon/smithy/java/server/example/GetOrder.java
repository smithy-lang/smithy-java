/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.UUID;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.example.model.GetOrderInput;
import software.amazon.smithy.java.server.example.model.GetOrderOutput;
import software.amazon.smithy.java.server.example.service.GetOrderOperation;

final class GetOrder implements GetOrderOperation {
    @Override
    public GetOrderOutput getOrder(GetOrderInput input, RequestContext context) {
        var order = OrderTracker.getOrderById(UUID.fromString(input.id()));
        if (order == null) {
            System.out.println("Order not found!");
            // TODO: Add error once error handling supported
        }
        return GetOrderOutput.builder()
            .id(input.id())
            .coffeeType(order.type())
            .status(order.status())
            .build();
    }
}
