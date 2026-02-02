/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.aws.client.core.settings.EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.StsEndpointSettings;
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
    public void putBuiltinProviders(Map<String, Function<Context, Object>> providers) {
        providers.putAll(AwsRulesBuiltin.BUILTINS);
    }

    @Override
    public void putBuiltinKeys(Map<String, Context.Key<?>> keys) {
        // Direct key access for simple builtins (avoids Function call overhead)
        keys.put("AWS::Region", RegionSetting.REGION);
        keys.put("AWS::UseDualStack", EndpointSettings.USE_DUAL_STACK);
        keys.put("AWS::UseFIPS", EndpointSettings.USE_FIPS);
        keys.put("AWS::Auth::AccountIdEndpointMode", EndpointSettings.ACCOUNT_ID_ENDPOINT_MODE);
        keys.put("AWS::S3::Accelerate", S3EndpointSettings.S3_ACCELERATE);
        keys.put("AWS::S3::DisableMultiRegionAccessPoints", S3EndpointSettings.S3_DISABLE_MULTI_REGION_ACCESS_POINTS);
        keys.put("AWS::S3::ForcePathStyle", S3EndpointSettings.S3_FORCE_PATH_STYLE);
        keys.put("AWS::S3::UseArnRegion", S3EndpointSettings.S3_USE_ARN_REGION);
        keys.put("AWS::S3::UseGlobalEndpoint", S3EndpointSettings.S3_USE_GLOBAL_ENDPOINT);
        keys.put("AWS::S3Control::UseArnRegion", S3EndpointSettings.S3_CONTROL_USE_ARN_REGION);
        keys.put("AWS::STS::UseGlobalEndpoint", StsEndpointSettings.STS_USE_GLOBAL_ENDPOINT);
        // Note: AWS::Auth::AccountId has fallback logic, so it uses the provider
    }

    @Override
    public Iterable<RulesFunction> getFunctions() {
        return Arrays.asList(AwsRulesFunction.values());
    }
}
