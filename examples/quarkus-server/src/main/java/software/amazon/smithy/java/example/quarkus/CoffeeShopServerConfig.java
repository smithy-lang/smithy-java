/*
 * Example file license header.
 * File header line two
 */

package software.amazon.smithy.java.example.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import software.amazon.smithy.java.example.quarkus.service.CoffeeShop;
import software.amazon.smithy.java.server.Service;

/**
 * The user-facing wiring for a Smithy-Java service inside Quarkus.
 *
 * <p>This producer returns a built {@link Service} (the generated
 * {@code CoffeeShop} stub). The {@code quarkus-smithy} extension
 * discovers every {@code @Produces Service} bean and mounts the
 * operations on Quarkus's main HTTP router via the upstream Vert.x
 * server — see the README for config options.
 *
 * <p>There is no user-supplied {@code URI} or port: the service shares
 * Quarkus's HTTP server, so configure host/port via the standard
 * {@code quarkus.http.host} / {@code quarkus.http.port} keys.
 */
@ApplicationScoped
public class CoffeeShopServerConfig {

    @Produces
    @Singleton
    Service coffeeShop() {
        return CoffeeShop.builder()
                .addCreateOrderOperation(new CreateOrder())
                .addGetMenuOperation(new GetMenu())
                .addGetOrderOperation(new GetOrder())
                .build();
    }
}
