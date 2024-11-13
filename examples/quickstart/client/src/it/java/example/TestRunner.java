package example;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.example.client.CoffeeShopClient;
import software.amazon.smithy.java.client.example.model.CoffeeType;
import software.amazon.smithy.java.client.example.model.CreateOrderInput;
import software.amazon.smithy.java.client.example.model.GetMenuInput;
import software.amazon.smithy.java.client.example.model.GetOrderInput;

public class TestRunner {
    private final CoffeeShopClient client = CoffeeShopClient.builder().build();

    @Test
    public void getMenu() {
        var menu = client.getMenu(GetMenuInput.builder().build());
        System.out.println(menu);
    }

    @Test
    public void createOrder() throws InterruptedException {
        // Create an order
        var createRequest = CreateOrderInput.builder().coffeeType(CoffeeType.COLD_BREW).build();
        var createResponse = client.createOrder(createRequest);
        System.out.println("Created request with id = " + createResponse.id());

        // Get the order. Should still be in progress.
        var getRequest = GetOrderInput.builder().id(createResponse.id()).build();
        var getResponse1 = client.getOrder(getRequest);
        System.out.println("Got order with id = " + getResponse1.id());

        // Give order some time to complete
        System.out.println("Waiting for order to complete....");
        TimeUnit.SECONDS.sleep(5);

        // Get the order again.
        var getResponse2 = client.getOrder(getRequest);
        System.out.println("Completed Order :" + getResponse2);
    }

    @Test
    void errorsOutIfOrderDoesNotExist() throws InterruptedException {
        var getRequest = GetOrderInput.builder().id(UUID.randomUUID().toString()).build();
        var orderNotFound = assertThrows(OrderNotFound.class, () -> client.getOrder(getRequest));
        assertEquals(orderNotFound.orderId(), getRequest.id());
    }
}
