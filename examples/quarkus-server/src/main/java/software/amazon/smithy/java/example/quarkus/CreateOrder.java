/*
 * Example file license header.
 * File header line two
 */

package software.amazon.smithy.java.example.quarkus;

import java.util.UUID;
import software.amazon.smithy.java.example.quarkus.model.CreateOrderInput;
import software.amazon.smithy.java.example.quarkus.model.CreateOrderOutput;
import software.amazon.smithy.java.example.quarkus.model.OrderStatus;
import software.amazon.smithy.java.example.quarkus.service.CreateOrderOperation;
import software.amazon.smithy.java.server.RequestContext;

/**
 * Create an order for a coffee.
 */
final class CreateOrder implements CreateOrderOperation {
    @Override
    public CreateOrderOutput createOrder(CreateOrderInput input, RequestContext context) {
        var id = UUID.randomUUID();

        OrderTracker.putOrder(new Order(id, input.getCoffeeType(), OrderStatus.IN_PROGRESS));

        return CreateOrderOutput.builder()
                .id(id.toString())
                .coffeeType(input.getCoffeeType())
                .status(OrderStatus.IN_PROGRESS)
                .build();
    }
}
