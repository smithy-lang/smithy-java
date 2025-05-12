/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.rulesengine.RulesExtension;
import software.amazon.smithy.java.client.rulesengine.RulesFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Adds AWS-specific functionality to the Smithy Rules engines, used to resolve endpoints.
 *
 * @link <a href="https://smithy.io/2.0/aws/rules-engine/index.html">AWS rules engine extensions</a>
 */
@SmithyUnstableApi
public class AwsRulesExtension implements RulesExtension {
    @Override
    public BiFunction<String, Context, Object> getBuiltinProvider() {
        return AwsRulesBuiltin.BUILTIN_PROVIDER;
    }

    @Override
    public List<RulesFunction> getFunctions() {
        return Arrays.asList(AwsRulesFunction.values());
    }
}
