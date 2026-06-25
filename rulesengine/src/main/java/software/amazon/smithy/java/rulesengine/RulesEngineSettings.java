/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.Map;
import software.amazon.smithy.java.context.Context;

/**
 * Context keys and trait keys for configuring the rules engine.
 */
public final class RulesEngineSettings {
    private RulesEngineSettings() {}

    /**
     * Bytecode to evaluate by the rules engine to resolve endpoints.
     */
    public static final Context.Key<Bytecode> BYTECODE = Context.key("Endpoint rules engine bytecode");

    /**
     * Rules engine builder used to customize the evaluator (e.g., add custom functions, builtins, etc.).
     */
    public static final Context.Key<RulesEngineBuilder> RULES_ENGINE_BUILDER = Context.key(
            "Rules engine builder used to customize the evaluator");

    /**
     * Additional endpoint parameters to pass to the rules engine.
     */
    public static final Context.Key<Map<String, Object>> ADDITIONAL_ENDPOINT_PARAMS = Context.key(
            "Additional endpoint parameters to pass to the rules engine");

    /**
     * Opt-in sink that receives a step-by-step trace of BDD endpoint resolution (inputs, each condition
     * evaluated, and the matched result) for tooling and debugging. When the key is absent the resolver
     * runs its original loop and does no extra work, so the normal resolution path is unaffected.
     *
     * @see BddTraceSink
     */
    public static final Context.Key<BddTraceSink> BDD_TRACE_SINK = Context.key(
            "Sink for tracing BDD endpoint resolution");
}
