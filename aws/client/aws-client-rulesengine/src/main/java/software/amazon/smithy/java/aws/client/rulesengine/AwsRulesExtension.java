/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.StsEndpointSettings;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.rulesengine.EndpointUtils;
import software.amazon.smithy.java.rulesengine.PropertyGetter;
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
        var schemes = buildAuthSchemes(entries);
        for (var s : schemes) {
            builder.addAuthScheme(s);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void extractEndpointAuthScheme(
            Endpoint.Builder builder,
            Context context,
            PropertyGetter authScheme,
            Map<String, List<String>> headers
    ) {
        String name = (String) authScheme.getProperty("name");
        String signingName = (String) authScheme.getProperty("signingName");
        String signingRegion = (String) authScheme.getProperty("signingRegion");
        Boolean disableDoubleEncoding = (Boolean) authScheme.getProperty("disableDoubleEncoding");
        List<String> signingRegionSet = (List<String>) authScheme.getProperty("signingRegionSet");

        EndpointAuthScheme result =
                buildAuthScheme(name, signingName, signingRegion, disableDoubleEncoding, signingRegionSet);
        if (result != null) {
            builder.addAuthScheme(result);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public EndpointAuthScheme createEndpointAuthScheme(
            Context context,
            PropertyGetter authScheme,
            Map<String, List<String>> headers
    ) {
        return buildAuthScheme(
                (String) authScheme.getProperty("name"),
                (String) authScheme.getProperty("signingName"),
                (String) authScheme.getProperty("signingRegion"),
                (Boolean) authScheme.getProperty("disableDoubleEncoding"),
                (List<String>) authScheme.getProperty("signingRegionSet"));
    }

    @SuppressWarnings("unchecked")
    private static List<EndpointAuthScheme> buildAuthSchemes(List<?> entries) {
        var result = new ArrayList<EndpointAuthScheme>(entries.size());
        for (Object entry : entries) {
            // Each entry is either a Map (when the rules engine emits via MAPN) or a
            // PropertyGetter (the STRUCTN fast path for small fixed-key blocks).
            // EndpointUtils.getProperty handles both.
            Object name = EndpointUtils.getProperty(entry, "name");
            if (!(name instanceof String schemeName) || schemeName.isEmpty()) {
                continue;
            }
            Object signingName = EndpointUtils.getProperty(entry, "signingName");
            Object signingRegion = EndpointUtils.getProperty(entry, "signingRegion");
            Object disableDoubleEncoding = EndpointUtils.getProperty(entry, "disableDoubleEncoding");
            Object signingRegionSet = EndpointUtils.getProperty(entry, "signingRegionSet");
            EndpointAuthScheme scheme = buildAuthScheme(
                    schemeName,
                    signingName instanceof String s ? s : null,
                    signingRegion instanceof String s ? s : null,
                    disableDoubleEncoding instanceof Boolean b ? b : null,
                    signingRegionSet instanceof List<?> set ? (List<String>) set : null);
            if (scheme != null) {
                result.add(scheme);
            }
        }
        return List.copyOf(result);
    }

    private static EndpointAuthScheme buildAuthScheme(
            String name,
            String signingName,
            String signingRegion,
            Boolean disableDoubleEncoding,
            List<String> signingRegionSet
    ) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return new DirectAuthScheme(
                toShapeIdName(name),
                emptyToNull(signingName),
                emptyToNull(signingRegion),
                disableDoubleEncoding,
                signingRegionSet);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Convert an endpoint-rule auth scheme name like {@code sigv4-s3express} into a valid
     * Smithy ShapeId-style id ({@code aws.auth#sigv4S3express}). Hyphenated segments are
     * upper-camel-joined; the leading segment is left as-is.
     */
    private static String toShapeIdName(String wireName) {
        switch (wireName) {
            case "sigv4":
                return "aws.auth#sigv4";
            case "sigv4a":
                return "aws.auth#sigv4a";
            case "sigv4-s3express":
                return "aws.auth#sigv4S3express";
            default:
                break;
        }
        StringBuilder sb = new StringBuilder("aws.auth#");
        boolean upperNext = false;
        for (int i = 0; i < wireName.length(); i++) {
            char c = wireName.charAt(i);
            if (c == '-') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }

    private record DirectAuthScheme(
            String authSchemeId,
            String signingName,
            String signingRegion,
            Boolean disableDoubleEncoding,
            List<String> signingRegionSet
    ) implements EndpointAuthScheme {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T property(Context.Key<T> property) {
            Object result = property == EndpointAuthSchemeSettings.SIGNING_NAME
                    ? signingName
                    : property == EndpointAuthSchemeSettings.SIGNING_REGION
                            ? signingRegion
                            : property == EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING
                                    ? disableDoubleEncoding
                                    : property == EndpointAuthSchemeSettings.SIGNING_REGION_SET
                                            ? signingRegionSet
                                            : null;
            return (T) result;
        }

        @Override
        public Set<Context.Key<?>> properties() {
            var result = new java.util.HashSet<Context.Key<?>>(4);
            if (signingName != null) {
                result.add(EndpointAuthSchemeSettings.SIGNING_NAME);
            }
            if (signingRegion != null) {
                result.add(EndpointAuthSchemeSettings.SIGNING_REGION);
            }
            if (disableDoubleEncoding != null) {
                result.add(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING);
            }
            if (signingRegionSet != null) {
                result.add(EndpointAuthSchemeSettings.SIGNING_REGION_SET);
            }
            return Set.copyOf(result);
        }
    }
}
