/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.aws.integrations.lambda.LambdaEndpoint;
import software.amazon.smithy.java.aws.integrations.lambda.ProxyRequest;
import software.amazon.smithy.java.aws.integrations.lambda.ProxyResponse;
import software.amazon.smithy.java.example.model.*;
import software.amazon.smithy.java.example.service.AddBeerOperation;
import software.amazon.smithy.java.example.service.BeerService;
import software.amazon.smithy.java.example.service.GetBeerOperation;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;

/*
 * This is a hypothetical implementation of a Smithy Lambda Handler that registers the LambdaEndpoint with an
 * implementation of the example beer service which uses RpcV2Cbor.
 */
public final class LambdaMain implements RequestHandler<ProxyRequest, ProxyResponse> {

    private static final LambdaEndpoint ENDPOINT = LambdaEndpoint.builder().service(getService()).build();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(LambdaMain.class);

    private final LambdaEndpoint endpoint;

    public LambdaMain() {
        this(ENDPOINT);
    }

    LambdaMain(LambdaEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    private static Service getService() {
        return BeerService.builder()
            .addAddBeerOperation(new AddBeerImpl())
            .addGetBeerOperation(new GetBeerImpl())
            .build();
    }

    @Override
    public ProxyResponse handleRequest(ProxyRequest proxyRequest, Context context) {
        return endpoint.handleRequest(proxyRequest, context);
    }

    private static final Map<Long, Beer> FRIDGE = new HashMap<>() {
        {
            put(1L, Beer.builder().name("Munich Helles").quantity(1).build());
        }
    };

    private static final AtomicInteger ID_GEN = new AtomicInteger(1);

    private static final class AddBeerImpl implements AddBeerOperation {
        @Override
        public AddBeerOutput addBeer(AddBeerInput input, RequestContext context) {
            LOGGER.info("AddBeer - {}", input);
            long id = ID_GEN.incrementAndGet();
            FRIDGE.put(id, input.beer());
            return AddBeerOutput.builder().id(id).build();
        }
    }

    private static final class GetBeerImpl implements GetBeerOperation {

        @Override
        public GetBeerOutput getBeer(GetBeerInput input, RequestContext context) {
            LOGGER.info("GetBeer - {}", input);
            return GetBeerOutput.builder().beer(FRIDGE.get(input.id())).build();
        }
    }
}
