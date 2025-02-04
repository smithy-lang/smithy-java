/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.example.model.*;
import software.amazon.smithy.java.server.example.service.AddBeerOperation;
import software.amazon.smithy.java.server.example.service.BeerService;
import software.amazon.smithy.java.server.example.service.GetBeerOperationAsync;

public class BasicServerExample {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Server server = Server.builder()
                .endpoints(URI.create("http://localhost:8080"))
                .addService(
                        BeerService.builder()
                                .addAddBeerOperation(new AddBeerImpl())
                                .addGetBeerOperation(new GetBeerImpl())
                                .build())
                .build();
        server.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.shutdown().get();
        }

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
