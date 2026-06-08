/*
 * Example file license header.
 * File header line two
 */

package software.amazon.smithy.java.example.quarkus;

import java.util.UUID;
import software.amazon.smithy.java.example.quarkus.model.GetOrderInput;
import software.amazon.smithy.java.example.quarkus.model.GetOrderOutput;
import software.amazon.smithy.java.example.quarkus.model.OrderNotFound;
import software.amazon.smithy.java.example.quarkus.service.GetOrderOperation;
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
