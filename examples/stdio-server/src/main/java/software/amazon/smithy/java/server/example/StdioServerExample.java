package software.amazon.smithy.java.server.example;

import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.example.model.AddBeerInput;
import software.amazon.smithy.java.server.example.model.AddBeerOutput;
import software.amazon.smithy.java.server.example.model.Beer;
import software.amazon.smithy.java.server.example.model.GetBeerInput;
import software.amazon.smithy.java.server.example.model.GetBeerOutput;
import software.amazon.smithy.java.server.example.service.AddBeerOperation;
import software.amazon.smithy.java.server.example.service.BeerService;
import software.amazon.smithy.java.server.example.service.GetBeerOperationAsync;
import software.amazon.smithy.java.server.iostream.IOStreamServerBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class StdioServerExample {

    public static void main(String[] args) {
        var server = new StdioServerExample(System.in, System.out);
        try {
            server.run();
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop();
        }
    }

    private final Server server;
    public StdioServerExample(InputStream is, OutputStream os) {
        this.server = new IOStreamServerBuilder()
            .input(is)
            .output(os)
            .addService(
                BeerService.builder()
                    .addAddBeerOperation(new AddBeerImpl())
                    .addGetBeerOperation(new GetBeerImpl())
                    .build())
            .build();
    }

    public void run() {
        server.start();
    }

    public void stop() {
        server.shutdown();
    }

    private static final Map<Long, Beer> FRIDGE = new HashMap<>();
    private static final AtomicInteger ID_GEN = new AtomicInteger();

    private static final class AddBeerImpl implements AddBeerOperation {
        @Override
        public AddBeerOutput addBeer(AddBeerInput input, RequestContext context) {
            long id = ID_GEN.incrementAndGet();
            FRIDGE.put(id, input.beer());
            return AddBeerOutput.builder().id(id).build();
        }
    }

    private static final class GetBeerImpl implements GetBeerOperationAsync {

        @Override
        public CompletableFuture<GetBeerOutput> getBeer(GetBeerInput input, RequestContext context) {
            return CompletableFuture.supplyAsync(
                () -> GetBeerOutput.builder().beer(FRIDGE.get(input.id())).build(),
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
        }
    }

}
