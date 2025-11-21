/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

public interface RulesEngineSettings<B extends ClientSetting<B>> extends ClientSetting<B> {
    /**
     * Bytecode to evaluate by the rules engine to resolve endpoints.
     */
    Context.Key<Bytecode> BYTECODE = Context.key("Endpoint rules engine bytecode");

    /**
     * Rules engine builder used to customize the evaluator (e.g., add custom functions, builtins, etc.).
     */
    Context.Key<RulesEngineBuilder> RULES_ENGINE_BUILDER = Context.key(
            "Rules engine builder used to customize the evaluator");
}
