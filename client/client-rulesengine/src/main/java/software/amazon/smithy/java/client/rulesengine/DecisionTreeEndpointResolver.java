/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.rulesengine.language.evaluation.RuleEvaluator;
import software.amazon.smithy.rulesengine.language.evaluation.value.EndpointValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;

/**
 * Resolves endpoints by interpreting the endpoint ruleset decision tree. This is significantly slower that using the
 * bytecode interpreter, but it's not practical for a dynamic client to compile a BDD then bytecode
 * (that's easily 150 ms+).
 */
final class DecisionTreeEndpointResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(DecisionTreeEndpointResolver.class);
    private static final ThreadLocal<UriFactory> THREAD_LOCAL_URI_FACTORY = ThreadLocal.withInitial(UriFactory::new);

    private final EndpointRuleSet rules;
    private final List<RulesExtension> extensions;
    private final ContextProvider operationContextParams = new ContextProvider.OrchestratingProvider();
    private final UriFactory uriFactory = THREAD_LOCAL_URI_FACTORY.get();

    // Pre-computed parameter metadata
    private final Map<Identifier, Value> defaultValues;
    private final Map<String, Identifier> paramNameToIdentifier;
    private final Map<Identifier, Function<Context, Object>> builtinById;

    DecisionTreeEndpointResolver(
            EndpointRuleSet rules,
            List<RulesExtension> extensions,
            Map<String, Function<Context, Object>> builtinProviders
    ) {
        this.rules = rules;
        this.extensions = extensions;

        // Pre-process parameters at construction time
        var params = rules.getParameters().toList();
        int paramCount = params.size();
        this.defaultValues = new HashMap<>(paramCount);
        this.paramNameToIdentifier = new HashMap<>(paramCount);
        this.builtinById = new HashMap<>();

        for (var param : params) {
            Identifier id = param.getName();
            String name = id.toString();
            paramNameToIdentifier.put(name, id);

            // Pre-convert default values to Value objects
            if (param.getDefault().isPresent()) {
                defaultValues.put(id, param.getDefault().get());
            }

            // Map builtins by identifier
            if (param.getBuiltIn().isPresent()) {
                String builtinName = param.getBuiltIn().get();
                Function<Context, Object> provider = builtinProviders.get(builtinName);
                if (provider != null) {
                    builtinById.put(id, provider);
                }
            }
        }
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        try {
            var operation = params.operation();

            // Start with defaults
            Map<Identifier, Value> input = new HashMap<>(defaultValues);

            // Collect and apply supplied parameters (typically just a few)
            Map<String, Object> endpointParams = new HashMap<>();
            ContextProvider.createEndpointParams(
                    endpointParams,
                    operationContextParams,
                    params.context(),
                    operation,
                    params.inputValue());

            // Convert supplied values and override defaults
            for (var e : endpointParams.entrySet()) {
                Identifier id = paramNameToIdentifier.get(e.getKey());
                if (id != null) {
                    input.put(id, EndpointUtils.convertToValue(e.getValue()));
                }
            }

            // Apply builtins only for parameters that need them
            var context = params.context();
            for (var entry : builtinById.entrySet()) {
                Identifier id = entry.getKey();
                if (!input.containsKey(id)) {
                    Object value = entry.getValue().apply(context);
                    if (value != null) {
                        input.put(id, EndpointUtils.convertToValue(value));
                    }
                }
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Resolving endpoint of {} using VM with params: {}", operation, input);
            }

            var result = RuleEvaluator.evaluate(rules, input);
            if (result instanceof EndpointValue ev) {
                return CompletableFuture.completedFuture(convertEndpoint(params, ev));
            }

            throw new IllegalStateException("Expected decision tree to return an endpoint, but found " + result);
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
