/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResult;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.example.client.CoffeeShopClient;
import software.amazon.smithy.java.example.model.CoffeeType;
import software.amazon.smithy.java.example.model.CreateOrderInput;
import software.amazon.smithy.java.example.model.GetMenuInput;
import software.amazon.smithy.java.example.model.GetOrderInput;
import software.amazon.smithy.java.example.model.OrderNotFound;
import software.amazon.smithy.java.example.orders.model.OrderStatus;

public class RoundTripTests {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CoffeeShopClient client;

    @BeforeAll
    public static void setup() throws InterruptedException {
        var server = new BasicServerExample();
        executor.execute(server);
        // Wait for server to start
        while (!serverListening(BasicServerExample.endpoint)) {
            TimeUnit.SECONDS.sleep(1);
        }
    }

    public static boolean serverListening(URI uri) {
        try (Socket ignored = new Socket(uri.getHost(), uri.getPort())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setupClient() {
        client = CoffeeShopClient.builder()
            .endpointResolver(EndpointResolver.staticEndpoint(BasicServerExample.endpoint))
            .addIdentityResolver(new TokenIdentityResolver())
            .build();
    }

    @Test
    void executesCorrectly() throws InterruptedException {
        var menu = client.getMenu(GetMenuInput.builder().build());
        var hasEspresso = menu.items().stream().anyMatch(item -> item.typeMember().equals(CoffeeType.ESPRESSO));
        assertTrue(hasEspresso);

        var createRequest = CreateOrderInput.builder().coffeeType(CoffeeType.COLD_BREW).build();
        var createResponse = client.createOrder(createRequest);
        assertEquals(CoffeeType.COLD_BREW, createResponse.coffeeType());
        System.out.println("Created request with id = " + createResponse.id());

        var getRequest = GetOrderInput.builder().id(createResponse.id()).build();
        var getResponse1 = client.getOrder(getRequest);
        assertEquals(getResponse1.status(), OrderStatus.IN_PROGRESS);

        // Give order some time to complete
        System.out.println("Waiting for order to complete....");
        TimeUnit.SECONDS.sleep(5);

        var getResponse2 = client.getOrder(getRequest);
        assertEquals(getResponse2.status(), OrderStatus.COMPLETED);
        System.out.println("Completed Order :" + getResponse2);
    }

    @Test
    void errorsOutIfOrderDoesNotExist() throws InterruptedException {
        var getRequest = GetOrderInput.builder().id(UUID.randomUUID().toString()).build();
        var orderNotFound = assertThrows(OrderNotFound.class, () -> client.getOrder(getRequest));
        assertEquals(orderNotFound.orderId(), getRequest.id());
    }

    @AfterAll
    public static void teardown() {
        executor.shutdownNow();
    }

    private static final class TokenIdentityResolver implements IdentityResolver<TokenIdentity> {
        @Override
        public CompletableFuture<IdentityResult<TokenIdentity>> resolveIdentity(
            AuthProperties requestProperties
        ) {
            // TODO: Update to demonstrate token refresh once interceptors supported by server.
            return CompletableFuture.completedFuture(IdentityResult.of(TokenIdentity.create("woo")));
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }
}
