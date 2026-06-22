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
     * Opt-in sink for capturing the resolved endpoint parameter values (parameter name to value) during
     * resolution. To use it, place a <em>mutable</em> map under this key before the call; the rules
     * engine fills it (via {@code putAll}) with the parameters it resolved, and the caller reads the map
     * back afterwards. When the key is absent the resolver does no extra work, so the normal resolution
     * path is unaffected. A map (rather than a boolean flag plus a separate result key) is used because
     * the resolution-time context is unmodifiable: the resolver cannot {@code put} a result onto it, it
     * can only mutate a container the caller already supplied. Intended for tooling/debugging (e.g. a
     * CLI "plan" mode that shows exactly which parameters drove endpoint resolution).
     */
    public static final Context.Key<Map<String, Object>> RESOLVED_ENDPOINT_PARAMS = Context.key(
            "Mutable sink for capturing resolved endpoint parameters during resolution");
}
