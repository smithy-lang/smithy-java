/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.rulesengine.traits.ContextParamTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

/**
 * Attempts to resolve endpoints using smithy.rules#endpointRuleSet or a {@link RulesProgram} compiled from this trait.
 */
public final class EndpointRulesPlugin implements ClientPlugin {

    public static final TraitKey<StaticContextParamsTrait> STATIC_CONTEXT_PARAMS_TRAIT =
            TraitKey.get(StaticContextParamsTrait.class);

    public static final TraitKey<OperationContextParamsTrait> OPERATION_CONTEXT_PARAMS_TRAIT =
            TraitKey.get(OperationContextParamsTrait.class);

    public static final TraitKey<ContextParamTrait> CONTEXT_PARAM_TRAIT = TraitKey.get(ContextParamTrait.class);

    public static final TraitKey<EndpointRuleSetTrait> ENDPOINT_RULESET_TRAIT =
            TraitKey.get(EndpointRuleSetTrait.class);

    private final RulesProgram program;

    private EndpointRulesPlugin(RulesProgram program) {
        this.program = program;
    }

    /**
     * Create a RulesEnginePlugin from a precompiled {@link RulesProgram}.
     *
     * <p>This is typically used by code-generated clients.
     *
     * @param program Program used to resolve endpoint.
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(RulesProgram program) {
        return new EndpointRulesPlugin(program);
    }

    /**
     * Creates an EndpointRulesPlugin that waits to create a program until configuring the client. It looks for the
     * relevant Smithy traits, and if found, compiles them and sets up a resolver. If the traits can't be found, the
     * resolver is not updated. If a resolver is already set, it is not changed.
     *
     * @return the plugin.
     */
    public static EndpointRulesPlugin create() {
        return new EndpointRulesPlugin(null);
    }

    /**
     * Gets the endpoint rules program that was compiled, or null if no rules were found on the service.
     *
     * @return the rules program or null.
     */
    public RulesProgram getProgram() {
        return program;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // Only modify the endpoint resolver if it isn't set already or if CUSTOM_ENDPOINT is set,
        // and if a program was provided.
        if (config.endpointResolver() == null || config.context().get(ClientContext.CUSTOM_ENDPOINT) != null) {
            if (program != null) {
                applyResolver(program, config);
            } else if (config.service() != null) {
                var ruleset = config.service().schema().getTrait(ENDPOINT_RULESET_TRAIT);
                if (ruleset != null) {
                    applyResolver(new RulesEngine().compile(ruleset.getEndpointRuleSet()), config);
                }
            }
        }
    }

    private void applyResolver(RulesProgram applyProgram, ClientConfig.Builder config) {
        config.endpointResolver(new EndpointRulesResolver(applyProgram));
    }
}
