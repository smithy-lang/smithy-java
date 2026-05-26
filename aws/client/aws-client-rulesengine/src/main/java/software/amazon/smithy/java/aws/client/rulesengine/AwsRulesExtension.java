/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.StsEndpointSettings;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.rulesengine.RulesExtension;
import software.amazon.smithy.java.rulesengine.RulesFunction;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Adds AWS-specific functionality to the Smithy Rules engines, used to resolve endpoints.
 *
 * @link <a href="https://smithy.io/2.0/aws/rules-engine/index.html">AWS rules engine extensions</a>
 */
@SmithyUnstableApi
public class AwsRulesExtension implements RulesExtension {

    /**
     * Memoize the conversion from raw {@code authSchemes} property bag → typed
     * {@link EndpointAuthScheme} list. The rules engine reconstructs the property maps on every
     * resolve call, so the same content shows up at this hook on every request — content-keyed
     * caching collapses the per-call work to one map lookup.
     *
     * <p>Bounded growth: one entry per unique {@code authSchemes} literal in any rule set the
     * process touches. For S3 today that's ~10 entries.
     */
    private static final ConcurrentHashMap<List<?>, List<EndpointAuthScheme>> AUTH_SCHEME_CACHE =
            new ConcurrentHashMap<>();

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

    /**
     * Convert the {@code authSchemes} endpoint property emitted by Endpoints 2.0 rule sets into
     * {@link EndpointAuthScheme} entries on the resolved endpoint. Each entry's
     * {@code signingName} / {@code signingRegion} / {@code disableDoubleEncoding} /
     * {@code signingRegionSet} fields are stored under the matching
     * {@link EndpointAuthSchemeSettings} typed keys so the client pipeline can merge them
     * into the signer's properties.
     *
     * <p>This is a deprecated mechanism kept alive for the four services that depend on it
     * (s3, ses, eventbridge, cloudfront-keyvaluestore); new services should use a custom
     * auth-scheme resolver instead.
     */
    @Override
    public void extractEndpointProperties(
            Endpoint.Builder builder,
            Context context,
            Map<String, Object> properties,
            Map<String, List<String>> headers
    ) {
        Object raw = properties.get("authSchemes");
        if (!(raw instanceof List<?> entries) || entries.isEmpty()) {
            return;
        }
        var schemes = AUTH_SCHEME_CACHE.computeIfAbsent(entries, AwsRulesExtension::buildAuthSchemes);
        for (var s : schemes) {
            builder.addAuthScheme(s);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EndpointAuthScheme> buildAuthSchemes(List<?> entries) {
        var result = new ArrayList<EndpointAuthScheme>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("name");
            if (!(name instanceof String schemeName) || schemeName.isEmpty()) {
                continue;
            }
            var schemeBuilder = EndpointAuthScheme.builder().authSchemeId("aws.auth#" + schemeName);

            Object signingName = map.get("signingName");
            if (signingName instanceof String s && !s.isEmpty()) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_NAME, s);
            }
            Object signingRegion = map.get("signingRegion");
            if (signingRegion instanceof String s && !s.isEmpty()) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_REGION, s);
            }
            Object disableDoubleEncoding = map.get("disableDoubleEncoding");
            if (disableDoubleEncoding instanceof Boolean b) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING, b);
            }
            Object signingRegionSet = map.get("signingRegionSet");
            if (signingRegionSet instanceof List<?> set) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_REGION_SET, (List<String>) set);
            }

            result.add(schemeBuilder.build());
        }
        return List.copyOf(result);
    }
}
