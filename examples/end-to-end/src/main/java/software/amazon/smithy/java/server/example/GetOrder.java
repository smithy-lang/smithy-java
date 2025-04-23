/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.UUID;
import software.amazon.smithy.java.example.etoe.model.GetOrderInput;
import software.amazon.smithy.java.example.etoe.model.GetOrderOutput;
import software.amazon.smithy.java.example.etoe.model.OrderNotFound;
import software.amazon.smithy.java.example.etoe.service.GetOrderOperation;
import software.amazon.smithy.java.server.RequestContext;

final class GetOrder implements GetOrderOperation {
    @Override
    public GetOrderOutput getOrder(GetOrderInput input, RequestContext context) {
        var order = OrderTracker.getOrderById(UUID.fromString(input.getId()));
        if (order == null) {
            throw OrderNotFound.builder()
                    .orderId(input.getId())
                    .message("Order not found")
                    .build();
        }
        return GetOrderOutput.builder()
                .id(input.getId())
                .coffeeType(order.type())
                .status(order.status())
                .build();
    }
}
