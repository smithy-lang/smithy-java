/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.example.model.*;
import software.amazon.smithy.java.server.example.service.AddBeerOperation;
import software.amazon.smithy.java.server.example.service.BeerService;
import software.amazon.smithy.java.server.example.service.GetBeerOperation;

/*
 * This is a hypothetical implementation of a Smithy Lambda Handler that uses the LambdaEndpoint with an
 * implementation of the example beer service.
 */
public final class LambdaMain implements RequestHandler<ProxyRequest, ProxyResponse> {

    private static final LambdaEndpoint ENDPOINT = new LambdaEndpoint(getService());
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
    public ProxyResponse handleRequest(ProxyRequest apiGatewayProxyRequestEvent, Context context) {
        return endpoint.handleRequest(apiGatewayProxyRequestEvent, context);
    }

    private static final Map<Long, Beer> FRIDGE = new HashMap<>(
        Map.of(
            1L,
            Beer.builder().name("Munich Helles").quantity(1).build()
        )
    );

    private static Long ID_GEN = 1L;

    private static final class AddBeerImpl implements AddBeerOperation {
        @Override
        public AddBeerOutput addBeer(AddBeerInput input, RequestContext context) {
            LOGGER.info("AddBeer - ", input);
            ID_GEN += 1;
            FRIDGE.put(ID_GEN, input.beer());
            return AddBeerOutput.builder().id(ID_GEN).build();
        }
    }

    private static final class GetBeerImpl implements GetBeerOperation {

        @Override
        public GetBeerOutput getBeer(GetBeerInput input, RequestContext context) {
            LOGGER.info("GetBeer - ", input);
            return GetBeerOutput.builder().beer(FRIDGE.get(input.id())).build();
        }
    }
}
