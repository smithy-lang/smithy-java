/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import software.amazon.smithy.java.rulesengine.EndpointUtils;
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

    private static final EndpointPropertyBinding EMPTY_ENDPOINT_PROPERTIES = new EndpointPropertyBinding() {};
    private static final Set<Context.Key<?>>[] AUTH_PROPERTY_SETS = createAuthPropertySets();

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

        for (var s : AUTH_SCHEME_CACHE.computeIfAbsent(entries, AwsRulesExtension::buildAuthSchemes)) {
            builder.addAuthScheme(s);
        }
    }

    @Override
    public EndpointPropertyBinding bindEndpointProperties(EndpointPropertyBinding.Properties properties) {
        if (!properties.isObject()) {
            return null;
        }

        EndpointPropertyBinding.Properties authSchemes = properties.get("authSchemes");
        if (authSchemes == null) {
            return EMPTY_ENDPOINT_PROPERTIES;
        }
        if (!authSchemes.isList()) {
            return null;
        }

        int count = authSchemes.size();
        if (count == 0) {
            return EMPTY_ENDPOINT_PROPERTIES;
        }

        var schemes = new AuthSchemeShape[count];
        for (int i = 0; i < count; i++) {
            AuthSchemeShape scheme = AuthSchemeShape.compile(authSchemes.get(i));
            if (scheme == null) {
                // A wholly dynamic entry has no statically visible fields. Preserve the materialized
                // path for that shape rather than guessing how to interpret its runtime value.
                return null;
            }
            schemes[i] = scheme;
        }

        return new EndpointPropertyBinding() {
            @Override
            public int authSchemeCount() {
                return schemes.length;
            }

            @Override
            public EndpointAuthScheme authScheme(int index, ResolutionValues values) {
                return schemes[index].resolve(values);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<EndpointAuthScheme> buildAuthSchemes(List<?> entries) {
        var result = new ArrayList<EndpointAuthScheme>(entries.size());

        // Indexed rather than for-each: the rules engine may hand over a lazy list view, and iterating it
        // would allocate an iterator on every resolution for no benefit.
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            // Each entry is either a Map (when the rules engine emits via MAPN) or a
            // PropertyGetter (the STRUCTN fast path for small fixed-key blocks).
            // EndpointUtils.getProperty handles both.
            Object name = EndpointUtils.getProperty(entry, "name");
            if (!(name instanceof String schemeName) || schemeName.isEmpty()) {
                continue;
            }

            // Endpoint rules emit names like "sigv4-s3express" with hyphens, but Smithy ShapeIds
            // (used as map keys for AuthScheme registration) only allow alphanumerics and '_'.
            // Map each hyphen + following segment to upper-camel ("sigv4-s3express" ->
            // "sigv4S3Express"). AuthScheme implementations need to use the same mapping for
            // their canonical id (e.g. S3ExpressAuthScheme.SCHEME_ID).
            var schemeBuilder = EndpointAuthScheme.builder().authSchemeId(toShapeIdName(schemeName));

            Object signingName = EndpointUtils.getProperty(entry, "signingName");
            if (signingName instanceof String s && !s.isEmpty()) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_NAME, s);
            }
            Object signingRegion = EndpointUtils.getProperty(entry, "signingRegion");
            if (signingRegion instanceof String s && !s.isEmpty()) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_REGION, s);
            }
            Object disableDoubleEncoding = EndpointUtils.getProperty(entry, "disableDoubleEncoding");
            if (disableDoubleEncoding instanceof Boolean b) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING, b);
            }
            Object signingRegionSet = EndpointUtils.getProperty(entry, "signingRegionSet");
            if (signingRegionSet instanceof List<?> set) {
                schemeBuilder.putProperty(EndpointAuthSchemeSettings.SIGNING_REGION_SET, (List<String>) set);
            }

            result.add(schemeBuilder.build());
        }

        return List.copyOf(result);
    }

    private static final class AuthSchemeShape {
        private static final AuthSchemeShape SKIPPED = new AuthSchemeShape(null, null, null, null, null);

        private final EndpointPropertyBinding.Properties name;
        private final EndpointPropertyBinding.Properties signingName;
        private final EndpointPropertyBinding.Properties signingRegion;
        private final EndpointPropertyBinding.Properties disableDoubleEncoding;
        private final EndpointPropertyBinding.Properties signingRegionSet;
        private final boolean isConstant;
        private final EndpointAuthScheme constant;

        AuthSchemeShape(
                EndpointPropertyBinding.Properties name,
                EndpointPropertyBinding.Properties signingName,
                EndpointPropertyBinding.Properties signingRegion,
                EndpointPropertyBinding.Properties disableDoubleEncoding,
                EndpointPropertyBinding.Properties signingRegionSet
        ) {
            this.name = name;
            this.signingName = signingName;
            this.signingRegion = signingRegion;
            this.disableDoubleEncoding = disableDoubleEncoding;
            this.signingRegionSet = signingRegionSet;
            this.isConstant = isConstant(name)
                    && isConstant(signingName)
                    && isConstant(signingRegion)
                    && isConstant(disableDoubleEncoding)
                    && isConstant(signingRegionSet);
            this.constant = isConstant ? create(null) : null;
        }

        /**
         * Compiles one statically visible list entry.
         *
         * @return the compiled entry, {@link #SKIPPED} for an entry the legacy path would ignore, or
         *     null when the entire entry is dynamic and requires materialization.
         */
        static AuthSchemeShape compile(EndpointPropertyBinding.Properties entry) {
            if (entry == null) {
                return null;
            }
            if (!entry.isObject()) {
                return entry.isConstant() ? SKIPPED : null;
            }

            EndpointPropertyBinding.Properties name = entry.get("name");
            if (name == null) {
                return SKIPPED;
            }
            return new AuthSchemeShape(
                    name,
                    entry.get("signingName"),
                    entry.get("signingRegion"),
                    entry.get("disableDoubleEncoding"),
                    entry.get("signingRegionSet"));
        }

        EndpointAuthScheme resolve(EndpointPropertyBinding.ResolutionValues values) {
            return isConstant ? constant : create(values);
        }

        @SuppressWarnings("unchecked")
        private EndpointAuthScheme create(EndpointPropertyBinding.ResolutionValues values) {
            String schemeName = nonEmptyString(resolve(name, values));
            if (schemeName == null) {
                return null;
            }

            String resolvedSigningName = nonEmptyString(resolve(signingName, values));
            String resolvedSigningRegion = nonEmptyString(resolve(signingRegion, values));
            Object doubleEncodingValue = resolve(disableDoubleEncoding, values);
            Boolean resolvedDisableDoubleEncoding = doubleEncodingValue instanceof Boolean value ? value : null;
            Object regionSetValue = resolve(signingRegionSet, values);
            var resolvedSigningRegionSet = regionSetValue instanceof List<?> value ? (List<String>) value : null;
            return new DirectAuthScheme(
                    toShapeIdName(schemeName),
                    resolvedSigningName,
                    resolvedSigningRegion,
                    resolvedDisableDoubleEncoding,
                    resolvedSigningRegionSet);
        }

        private static boolean isConstant(EndpointPropertyBinding.Properties property) {
            return property == null || property.isConstant();
        }

        private static Object resolve(
                EndpointPropertyBinding.Properties property,
                EndpointPropertyBinding.ResolutionValues values
        ) {
            return property == null
                    ? null
                    : property.isConstant() ? property.constantValue() : property.resolve(values);
        }

        private static String nonEmptyString(Object value) {
            return value instanceof String string && !string.isEmpty() ? string : null;
        }
    }

    private record DirectAuthScheme(
            String authSchemeId,
            String signingName,
            String signingRegion,
            Boolean disableDoubleEncoding,
            List<String> signingRegionSet) implements EndpointAuthScheme {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T property(Context.Key<T> property) {
            if (property == EndpointAuthSchemeSettings.SIGNING_NAME) {
                return (T) signingName;
            } else if (property == EndpointAuthSchemeSettings.SIGNING_REGION) {
                return (T) signingRegion;
            } else if (property == EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING) {
                return (T) disableDoubleEncoding;
            } else if (property == EndpointAuthSchemeSettings.SIGNING_REGION_SET) {
                return (T) signingRegionSet;
            }
            return null;
        }

        @Override
        public Set<Context.Key<?>> properties() {
            int mask = signingName == null ? 0 : 1;
            mask |= signingRegion == null ? 0 : 2;
            mask |= disableDoubleEncoding == null ? 0 : 4;
            mask |= signingRegionSet == null ? 0 : 8;
            return AUTH_PROPERTY_SETS[mask];
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Context.Key<?>>[] createAuthPropertySets() {
        var result = (Set<Context.Key<?>>[]) new Set<?>[16];
        for (int mask = 0; mask < result.length; mask++) {
            var keys = new HashSet<Context.Key<?>>(4);
            if ((mask & 1) != 0) {
                keys.add(EndpointAuthSchemeSettings.SIGNING_NAME);
            }
            if ((mask & 2) != 0) {
                keys.add(EndpointAuthSchemeSettings.SIGNING_REGION);
            }
            if ((mask & 4) != 0) {
                keys.add(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING);
            }
            if ((mask & 8) != 0) {
                keys.add(EndpointAuthSchemeSettings.SIGNING_REGION_SET);
            }
            result[mask] = Set.copyOf(keys);
        }
        return result;
    }

    /**
     * Convert an endpoint-rule auth scheme name like {@code sigv4-s3express} into a valid
     * Smithy ShapeId-style id ({@code aws.auth#sigv4S3Express}). Hyphenated segments are
     * upper-camel-joined; the leading segment is left as-is.
     */
    private static String toShapeIdName(String wireName) {
        return switch (wireName) {
            case "sigv4" -> "aws.auth#sigv4";
            case "sigv4a" -> "aws.auth#sigv4a";
            case "sigv4-s3express" -> "aws.auth#sigv4S3express";
            default -> {
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
                yield sb.toString();
            }
        };
    }
}
