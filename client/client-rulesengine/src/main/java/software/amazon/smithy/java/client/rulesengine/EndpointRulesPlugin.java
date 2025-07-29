/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.logic.bdd.BddTrait;
import software.amazon.smithy.rulesengine.traits.ContextParamTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

/**
 * Attempts to resolve endpoints using smithy.rules#bdd, smithy.rules#endpointRuleSet, or a precompiled
 * {@link Bytecode}.
 */
public final class EndpointRulesPlugin implements ClientPlugin {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EndpointRulesPlugin.class);

    public static final Context.Key<Map<String, Object>> ADDITIONAL_ENDPOINT_PARAMS = Context.key(
            "Additional endpoint parameters to pass to the rules engine");

    public static final TraitKey<StaticContextParamsTrait> STATIC_CONTEXT_PARAMS_TRAIT =
            TraitKey.get(StaticContextParamsTrait.class);

    public static final TraitKey<OperationContextParamsTrait> OPERATION_CONTEXT_PARAMS_TRAIT =
            TraitKey.get(OperationContextParamsTrait.class);

    public static final TraitKey<ContextParamTrait> CONTEXT_PARAM_TRAIT = TraitKey.get(ContextParamTrait.class);

    public static final TraitKey<EndpointRuleSetTrait> ENDPOINT_RULESET_TRAIT =
            TraitKey.get(EndpointRuleSetTrait.class);

    public static final TraitKey<BddTrait> BDD_TRAIT = TraitKey.get(BddTrait.class);

    private final Bytecode bytecode;
    private final RulesEngineBuilder engine;

    private EndpointRulesPlugin(Bytecode bytecode, RulesEngineBuilder engine) {
        this.bytecode = bytecode;
        this.engine = engine;
    }

    /**
     * Create a RulesEnginePlugin from a precompiled {@link Bytecode}.
     *
     * <p>This is typically used by code-generated clients.
     *
     * @param bytecode Bytecode used to resolve endpoint.
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(Bytecode bytecode) {
        Objects.requireNonNull(bytecode, "RulesBytecode must not be null");
        return new EndpointRulesPlugin(bytecode, null);
    }

    /**
     * Creates an EndpointRulesPlugin that waits to create a program until configuring the client. It looks for the
     * relevant Smithy traits, and if found, compiles them and sets up a resolver. If the traits can't be found, the
     * resolver is not updated. If a resolver is already set, it is not changed.
     *
     * @return the plugin.
     */
    public static EndpointRulesPlugin create() {
        return create(new RulesEngineBuilder());
    }

    /**
     * Creates an EndpointRulesPlugin that waits to create a program until configuring the client. It looks for the
     * relevant Smithy traits, and if found, compiles them and sets up a resolver. If the traits can't be found, the
     * resolver is not updated. If a resolver is already set, it is not changed.
     *
     * @param engine RulesEngine to use when creating programs.
     * @return the plugin.
     */
    public static EndpointRulesPlugin create(RulesEngineBuilder engine) {
        return new EndpointRulesPlugin(null, engine);
    }

    /**
     * Gets the endpoint rules bytecode that was compiled, or null if no rules were found on the service.
     *
     * @return the rules program or null.
     */
    public Bytecode getBytecode() {
        return bytecode;
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

        if (usePlugin) {
            EndpointResolver resolver = null;

            if (bytecode == null && config.service() != null) {
                var bddTrait = config.service().schema().getTrait(BDD_TRAIT);
                if (bddTrait != null) {
                    LOGGER.debug("Found endpoint BDD trait on service: {}", config.service());
                    resolver = new BytecodeEndpointResolver(
                            engine.compile(bddTrait),
                            engine.getExtensions(),
                            engine.getBuiltinProviders());
                } else {
                    var ruleset = config.service().schema().getTrait(ENDPOINT_RULESET_TRAIT);
                    if (ruleset != null) {
                        LOGGER.debug("Using decision tree based endpoint resolver for service: {}", config.service());
                        resolver = new DecisionTreeEndpointResolver(
                                ruleset.getEndpointRuleSet(),
                                engine.getExtensions(),
                                engine.getBuiltinProviders());
                    }
                }
            }

            if (resolver != null) {
                config.endpointResolver(resolver);
                LOGGER.debug("Applying EndpointRulesResolver to client: {}", config.service());
            }
        }
    }
}
