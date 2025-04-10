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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.example.etoe.client.CoffeeShopClient;
import software.amazon.smithy.java.example.etoe.model.CoffeeType;
import software.amazon.smithy.java.example.etoe.model.CreateOrderInput;
import software.amazon.smithy.java.example.etoe.model.GetMenuInput;
import software.amazon.smithy.java.example.etoe.model.GetOrderInput;
import software.amazon.smithy.java.example.etoe.model.OrderNotFound;
import software.amazon.smithy.java.example.etoe.model.OrderStatus;

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

        // Complete the order
        OrderTracker.completeOrder(getResponse1.id());

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
}
