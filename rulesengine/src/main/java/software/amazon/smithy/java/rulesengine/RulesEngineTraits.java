/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.rulesengine.traits.ContextParamTrait;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

public final class RulesEngineTraits {

    public static final TraitKey<StaticContextParamsTrait> STATIC_CONTEXT_PARAMS_TRAIT = TraitKey.get(
            StaticContextParamsTrait.class);
    public static final TraitKey<OperationContextParamsTrait> OPERATION_CONTEXT_PARAMS_TRAIT = TraitKey.get(
            OperationContextParamsTrait.class);
    public static final TraitKey<ContextParamTrait> CONTEXT_PARAM_TRAIT = TraitKey.get(ContextParamTrait.class);
    public static final TraitKey<EndpointRuleSetTrait> ENDPOINT_RULESET_TRAIT = TraitKey.get(
            EndpointRuleSetTrait.class);
    public static final TraitKey<EndpointBddTrait> BDD_TRAIT = TraitKey.get(EndpointBddTrait.class);

    private RulesEngineTraits() {}
}
