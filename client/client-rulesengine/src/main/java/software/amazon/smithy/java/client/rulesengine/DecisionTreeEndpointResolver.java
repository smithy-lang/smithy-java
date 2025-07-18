/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.evaluation.RuleEvaluator;
import software.amazon.smithy.rulesengine.language.evaluation.value.EndpointValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;

/**
 * Resolves endpoints by interpreting the endpoint ruleset decision tree. Slower, but no startup penalty and
 * always available.
 */
final class DecisionTreeEndpointResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BytecodeEndpointResolver.class);

    private final EndpointRuleSet rules;
    private final List<RulesExtension> extensions;
    private final ContextProvider operationContextParams = new ContextProvider.OrchestratingProvider();
    private final UriFactory uriFactory;

    DecisionTreeEndpointResolver(EndpointRuleSet rules, List<RulesExtension> extensions, UriFactory uriFactory) {
        this.rules = rules;
        this.extensions = extensions;
        this.uriFactory = uriFactory;
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        try {
            var operation = params.operation();
            Map<String, Object> endpointParams = new HashMap<>();
            ContextProvider.createEndpointParams(
                    endpointParams,
                    operationContextParams,
                    params.context(),
                    operation,
                    params.inputValue());
            LOGGER.debug("Resolving endpoint of {} using VM with params: {}", operation, endpointParams);
            Map<Identifier, Value> input = new HashMap<>(endpointParams.size());
            for (var e : endpointParams.entrySet()) {
                input.put(Identifier.of(e.getKey()), EndpointUtils.convertToValue(e.getValue()));
            }
            var result = RuleEvaluator.evaluate(rules, input);
            if (result instanceof EndpointValue ev) {
                return CompletableFuture.completedFuture(convertEndpoint(params, ev));
            } else {
                throw new IllegalStateException("Expected decision tree to return an endpoint, but found " + result);
            }
        } catch (RulesEvaluationError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Endpoint convertEndpoint(EndpointResolverParams params, EndpointValue ev) {
        var builder = Endpoint.builder();
        builder.uri(uriFactory.createUri(ev.getUrl()));

        Map<String, Object> properties;
        if (ev.getProperties().isEmpty()) {
            properties = Map.of();
        } else {
            properties = new HashMap<>(ev.getProperties().size());
            for (var e : ev.getProperties().entrySet()) {
                properties.put(e.getKey(), e.getValue().toObject());
            }
        }

        for (var extension : extensions) {
            extension.extractEndpointProperties(builder, params.context(), properties, ev.getHeaders());
        }

        return builder.build();
    }
}
