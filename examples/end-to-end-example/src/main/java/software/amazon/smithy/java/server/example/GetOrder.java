/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.UUID;
import software.amazon.smithy.java.example.model.GetOrderInput;
import software.amazon.smithy.java.example.model.GetOrderOutput;
import software.amazon.smithy.java.example.model.OrderNotFound;
import software.amazon.smithy.java.example.service.GetOrderOperation;
import software.amazon.smithy.java.server.RequestContext;

final class GetOrder implements GetOrderOperation {
    private final ArnMapper arnMapper = new ArnMapper("cafe", "us-east-1");

    @Override
    public GetOrderOutput getOrder(GetOrderInput input, RequestContext context) {
        var operation = new software.amazon.smithy.java.example.model.GetOrder();
        System.out.println("ACTION NAME: " + arnMapper.getActionName(input, operation));
        try {
            System.out.println("RESOURCE ARN: " + arnMapper.getResourceArn(input, operation));
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        var order = OrderTracker.getOrderById(UUID.fromString(input.id()));
        if (order == null) {
            throw OrderNotFound.builder()
                .orderId(input.id())
                .message("Order not found")
                .build();
        }
        return GetOrderOutput.builder()
            .id(input.id())
            .coffeeType(order.type())
            .status(order.status())
            .build();
    }
}
