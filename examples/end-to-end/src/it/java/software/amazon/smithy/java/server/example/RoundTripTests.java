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
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.example.etoe.client.CoffeeShopClient;
import software.amazon.smithy.java.example.etoe.model.CoffeeType;
import software.amazon.smithy.java.example.etoe.model.CreateOrderInput;
import software.amazon.smithy.java.example.etoe.model.GetMenuInput;
import software.amazon.smithy.java.example.etoe.model.GetOrderInput;
import software.amazon.smithy.java.example.etoe.model.OrderNotFound;
import software.amazon.smithy.java.example.etoe.model.OrderStatus;
import software.amazon.smithy.model.node.ObjectNode;

public class RoundTripTests {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeAll
    public static void setup() throws InterruptedException {
        var server = new BasicServerExample();
        executor.execute(server);
        // Wait for server to start
        while (!serverListening(BasicServerExample.endpoint)) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    public static boolean serverListening(URI uri) {
        try (Socket ignored = new Socket(uri.getHost(), uri.getPort())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    CoffeeShopClient setupClient(String name) {
        System.out.println("creating coffee shop client using " + name);
        ClientTransport<?,?> transport;
        if ("java".equals(name)) {
            transport = new JavaHttpClientTransport.Factory()
                    .createTransport(Document.of(Collections.emptyMap()));
        } else if ("netty".equals(name)) {
            transport = new NettyHttpClientTransport.Factory()
                    .createTransport(Document.of(Collections.emptyMap()));
        } else {
            throw new IllegalArgumentException("Unknown HTTP transport " + name);
        }
        return CoffeeShopClient.builder()
                .endpointResolver(EndpointResolver.staticEndpoint(BasicServerExample.endpoint))
                .transport(transport)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java", "netty"
    })
    void executesCorrectly(String name) {
        var client = setupClient(name);
        var menu = client.getMenu(GetMenuInput.builder().build());
        var menuItems = menu.getItems();
        var hasEspresso = menuItems.stream().anyMatch(item -> item.getType().equals(CoffeeType.ESPRESSO));
        assertTrue(hasEspresso);

        var createRequest = CreateOrderInput.builder().coffeeType(CoffeeType.COLD_BREW).build();
        var createResponse = client.createOrder(createRequest);
        assertEquals(CoffeeType.COLD_BREW, createResponse.getCoffeeType());
        System.out.println("Created request with id = " + createResponse.getId());

        var getRequest = GetOrderInput.builder().id(createResponse.getId()).build();
        var getResponse1 = client.getOrder(getRequest);
        assertEquals(getResponse1.getStatus(), OrderStatus.IN_PROGRESS);

        // Complete the order
        OrderTracker.completeOrder(getResponse1.getId());

        var getResponse2 = client.getOrder(getRequest);
        assertEquals(getResponse2.getStatus(), OrderStatus.COMPLETED);
        System.out.println("Completed Order :" + getResponse2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java", "netty"
    })
    void errorsOutIfOrderDoesNotExist(String name) {
        var client = setupClient(name);
        var getRequest = GetOrderInput.builder().id(UUID.randomUUID().toString()).build();
        var orderNotFound = assertThrows(OrderNotFound.class, () -> client.getOrder(getRequest));
        assertEquals(orderNotFound.getOrderId(), getRequest.getId());
    }

    @AfterAll
    public static void teardown() {
        executor.shutdownNow();
    }
}
