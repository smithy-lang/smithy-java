/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Endpoint resolver that uses the endpoint rules engine.
 */
final class EndpointRulesResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EndpointRulesResolver.class);

    private final RulesProgram program;
    private final ContextProvider operationContextParams = new ContextProvider.OrchestratingProvider();

    EndpointRulesResolver(RulesProgram program) {
        this.program = program;
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        try {
            var operation = params.operation();
            var endpointParams = createEndpointParams(params.context(), operation, params.inputValue());
            LOGGER.debug("Resolving endpoint of {} using VM with params: {}", operation, endpointParams);
            return CompletableFuture.completedFuture(program.resolveEndpoint(params.context(), endpointParams));
        } catch (RulesEvaluationError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> createEndpointParams(
            Context context,
            ApiOperation<?, ?> operation,
            SerializableStruct input
    ) {
        Map<String, Object> params = new HashMap<>();
        operationContextParams.addContext(operation, input, params);

        var additionalParams = context.get(EndpointRulesPlugin.ADDITIONAL_ENDPOINT_PARAMS);
        if (additionalParams != null) {
            params.putAll(additionalParams);
        }

        return params;
    }
}
