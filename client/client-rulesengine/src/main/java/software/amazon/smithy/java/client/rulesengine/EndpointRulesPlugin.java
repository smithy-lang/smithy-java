/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ServiceShape;
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
     * Uses an endpoint ruleset dynamically, but only if the smithy.rules#endpointRuleSet trait is applied to the
     * service.
     *
     * <p>This is typically used with a DynamicClient.
     *
     * @param service Service to call.
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(ApiService service) {
        return from(service, new RulesEngine());
    }

    /**
     * Uses an endpoint ruleset dynamically, but only if the smithy.rules#endpointRuleSet trait is applied to the
     * service.
     *
     * <p>This is typically used with a DynamicClient.
     *
     * @param service Service to call.
     * @param engine RulesEngine to use (used to register custom functions, providers, and extensions not through SPI).
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(ApiService service, RulesEngine engine) {
        return createFromTraits(service.schema().getTrait(ENDPOINT_RULESET_TRAIT), engine);
    }

    /**
     * Uses an endpoint ruleset dynamically, but only if the smithy.rules#endpointRuleSet trait is applied to the
     * service.
     *
     * <p>This is typically used with a DynamicClient.
     *
     * @param service Service to call.
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(ServiceShape service) {
        return from(service, new RulesEngine());
    }

    /**
     * Uses an endpoint ruleset dynamically, but only if the smithy.rules#endpointRuleSet trait is applied to the
     * service.
     *
     * <p>This is typically used with a DynamicClient.
     *
     * @param service Service to call.
     * @param engine RulesEngine to use (used to register custom functions, providers, and extensions not through SPI).
     * @return the rules engine plugin.
     */
    public static EndpointRulesPlugin from(ServiceShape service, RulesEngine engine) {
        return createFromTraits(service.getTrait(EndpointRuleSetTrait.class).orElse(null), engine);
    }

    private static EndpointRulesPlugin createFromTraits(EndpointRuleSetTrait ruleset, RulesEngine engine) {
        return ruleset == null
                ? new EndpointRulesPlugin(null)
                : from(engine.compile(ruleset.getEndpointRuleSet()));
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // Only modify the endpoint resolver if it isn't set already and if a program was provided.
        if (config.endpointResolver() == null && program != null) {
            config.endpointResolver(new EndpointRulesResolver(program));
        }
    }
}
