/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.EndpointContext;

final class StdExtension implements RulesExtension {
    @Override
    public void putBuiltinProviders(Map<String, Function<Context, Object>> providers) {
        providers.put("SDK::Endpoint", ctx -> {
            var result = ctx.get(EndpointContext.CUSTOM_ENDPOINT);
            return result == null ? null : result.uri().toString();
        });
    }
}
