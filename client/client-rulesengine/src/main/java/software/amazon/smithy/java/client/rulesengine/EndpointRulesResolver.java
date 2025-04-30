/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;

final class EndpointRulesResolver implements EndpointResolver {

    private final RulesProgram program;
    private final ConcurrentMap<ShapeId, Map<String, Object>> STATIC_PARAMS = new ConcurrentHashMap<>();

    EndpointRulesResolver(RulesProgram program) {
        this.program = program;
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        var operation = params.operation().schema();
        var endpointParams = createEndpointParams(operation);

        try {
            return CompletableFuture.completedFuture(program.resolveEndpoint(params.context(), endpointParams));
        } catch (RulesEvaluationError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> createEndpointParams(Schema operation) {
        var staticParams = getStaticParams(operation);
        // TODO: Grab input from RulesEnginePlugin.OPERATION_CONTEXT_PARAMS_TRAIT
        // TODO: Grab input from RulesEnginePlugin.CONTEXT_PARAM_TRAIT
        // TODO: We need some way to provide the SDK::Endpoint builtin.
        return new HashMap<>(staticParams);
    }

    private Map<String, Object> getStaticParams(Schema operation) {
        var id = operation.id();
        var staticParams = STATIC_PARAMS.get(id);
        if (staticParams != null) {
            return staticParams;
        } else {
            staticParams = computeStaticParams(operation);
            var fresh = STATIC_PARAMS.putIfAbsent(id, staticParams);
            return fresh == null ? staticParams : fresh;
        }
    }

    private Map<String, Object> computeStaticParams(Schema operation) {
        var staticParamsTrait = operation.getTrait(EndpointRulesPlugin.STATIC_CONTEXT_PARAMS_TRAIT);
        if (staticParamsTrait == null) {
            return Map.of();
        }

        Map<String, Object> result = new HashMap<>(staticParamsTrait.getParameters().size());
        for (var entry : staticParamsTrait.getParameters().entrySet()) {
            result.put(entry.getKey(), EndpointUtils.convertNodeInput(entry.getValue().getValue()));
        }
        return result;
    }
}
