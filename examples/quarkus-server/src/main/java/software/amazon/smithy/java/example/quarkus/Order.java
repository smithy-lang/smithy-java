/*
 * Example file license header.
 * File header line two
 */

package software.amazon.smithy.java.example.quarkus;

import java.util.UUID;
import software.amazon.smithy.java.example.quarkus.model.CoffeeType;
import software.amazon.smithy.java.example.quarkus.model.OrderStatus;

/**
 * A coffee drink order.
 *
 * @param id UUID of the order
 * @param type Type of drink for the order
 * @param status status of the order.
 */
public record Order(UUID id, CoffeeType type, OrderStatus status) {

    Order complete() {
        return new Order(id, type, OrderStatus.COMPLETED);
    }
}
