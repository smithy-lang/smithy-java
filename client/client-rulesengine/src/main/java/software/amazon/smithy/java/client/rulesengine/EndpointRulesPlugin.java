/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.Map;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.traits.ContextParamTrait;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

/**
 * Attempts to resolve endpoints using smithy.rules#bdd, smithy.rules#endpointRuleSet, or a precompiled
 * {@link Bytecode}.
 */
public final class EndpointRulesPlugin implements AutoClientPlugin {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EndpointRulesPlugin.class);

    public static final Context.Key<Map<String, Object>> ADDITIONAL_ENDPOINT_PARAMS = Context.key(
            "Additional endpoint parameters to pass to the rules engine");

    public static final TraitKey<StaticContextParamsTrait> STATIC_CONTEXT_PARAMS_TRAIT = TraitKey.get(
            StaticContextParamsTrait.class);
    public static final TraitKey<OperationContextParamsTrait> OPERATION_CONTEXT_PARAMS_TRAIT = TraitKey.get(
            OperationContextParamsTrait.class);
    public static final TraitKey<ContextParamTrait> CONTEXT_PARAM_TRAIT = TraitKey.get(ContextParamTrait.class);
    public static final TraitKey<EndpointRuleSetTrait> ENDPOINT_RULESET_TRAIT = TraitKey.get(
            EndpointRuleSetTrait.class);
    public static final TraitKey<EndpointBddTrait> BDD_TRAIT = TraitKey.get(EndpointBddTrait.class);

    @Override
    public Phase getPluginPhase() {
        return Phase.DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // Only modify the endpoint resolver if it isn't set already or if CUSTOM_ENDPOINT is set,
        // and if a program was provided.
        boolean usePlugin = false;
        if (config.endpointResolver() == null) {
            usePlugin = true;
            LOGGER.debug("Trying to use EndpointRulesPlugin resolver because endpointResolver is null");
        } else if (config.context().get(ClientContext.CUSTOM_ENDPOINT) != null) {
            usePlugin = true;
            LOGGER.debug("Trying to use EndpointRulesPlugin resolver because CUSTOM_ENDPOINT is set");
        }

        if (!usePlugin) {
            LOGGER.debug("Not using EndpointRulesPlugin");
            return;
        }

        var context = config.context();
        var e = context.get(RulesEngineSettings.RULES_ENGINE_BUILDER);
        if (e == null) {
            e = new RulesEngineBuilder();
        }

        var bytecode = context.get(RulesEngineSettings.BYTECODE);
        EndpointResolver resolver = null;

        // If bytecode wasn't explicitly given, then try to compile it from traits.
        if (bytecode != null) {
            LOGGER.debug("Using explicitly provided bytecode: {}", config.service());
            resolver = new BytecodeEndpointResolver(bytecode, e.getExtensions(), e.getBuiltinProviders());
        } else if (config.service() != null) {
            var bddTrait = config.service().schema().getTrait(BDD_TRAIT);
            if (bddTrait != null) {
                LOGGER.debug("Found endpoint BDD trait on service: {}", config.service());
                bytecode = e.compile(bddTrait);
                resolver = new BytecodeEndpointResolver(bytecode, e.getExtensions(), e.getBuiltinProviders());
                context.put(RulesEngineSettings.BYTECODE, bytecode);
            } else {
                var rs = config.service().schema().getTrait(ENDPOINT_RULESET_TRAIT);
                if (rs != null) {
                    LOGGER.debug("Using decision tree based endpoint resolver for service: {}", config.service());
                    resolver = new DecisionTreeEndpointResolver(
                            rs.getEndpointRuleSet(),
                            e.getExtensions(),
                            e.getBuiltinProviders());
                }
            }
        }

        if (resolver != null) {
            config.endpointResolver(resolver);
            LOGGER.info("Applying EndpointRulesResolver to client: {}", config.service());
        }
    }
}
