/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.examples;

import com.google.auto.service.AutoService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import software.amazon.smithy.java.aws.integrations.lambda.SmithyServiceProvider;
import software.amazon.smithy.java.examples.model.AddBeerInput;
import software.amazon.smithy.java.examples.model.AddBeerOutput;
import software.amazon.smithy.java.examples.model.Beer;
import software.amazon.smithy.java.examples.model.GetBeerInput;
import software.amazon.smithy.java.examples.model.GetBeerOutput;
import software.amazon.smithy.java.examples.service.AddBeerOperation;
import software.amazon.smithy.java.examples.service.BeerService;
import software.amazon.smithy.java.examples.service.GetBeerOperation;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;

/*
 * This is a hypothetical implementation of an SmithyServiceProvider for the Beer Service.
 * It provides the Lambda endpoint with the service implementation, and is registered when the endpoint is created.
 */
@AutoService(SmithyServiceProvider.class)
public final class BeerServiceProvider implements SmithyServiceProvider {

    private static final Logger LOGGER = Logger.getLogger(BeerServiceProvider.class.getName());
    private static final Service SERVICE;
    private static final Map<String, Beer> FRIDGE = new HashMap<>(
            Map.of("TXVuaWNoIEhlbGxlcw==", Beer.builder().name("Munich Helles").quantity(1).build()));
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    static {
        // This is statically initialized such that Lambda can re-use it across invocations
        SERVICE = BeerService.builder()
                .addAddBeerOperation(new AddBeerImpl())
                .addGetBeerOperation(new GetBeerImpl())
                .build();
    }

    public BeerServiceProvider() {}

    @Override
    public Service get() {
        return SERVICE;
    }

    private static final class AddBeerImpl implements AddBeerOperation {
        @Override
        public AddBeerOutput addBeer(AddBeerInput input, RequestContext context) {
            LOGGER.info("AddBeer - " + input);
            String id = ENCODER.encodeToString(input.beer().name().getBytes(StandardCharsets.UTF_8));
            FRIDGE.put(id, input.beer());
            return AddBeerOutput.builder().id(id).build();
        }
    }

    private static final class GetBeerImpl implements GetBeerOperation {
        @Override
        public GetBeerOutput getBeer(GetBeerInput input, RequestContext context) {
            LOGGER.info("GetBeer - " + input);
            return GetBeerOutput.builder().beer(FRIDGE.get(input.id())).build();
        }
    }
}
