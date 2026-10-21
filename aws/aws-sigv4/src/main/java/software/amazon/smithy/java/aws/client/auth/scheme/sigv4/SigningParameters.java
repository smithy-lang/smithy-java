/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.time.Clock;
import java.util.List;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Endpoint auth scheme settings take precedence over client settings
 * (Endpoints 2.0 implementation SEP, "Endpoint Properties (authSchemes)").
 */
final class SigningParameters {

    private SigningParameters() {}

    static String signingName(Context properties) {
        var name = properties.get(EndpointAuthSchemeSettings.SIGNING_NAME);
        return name != null ? name : properties.expect(SigV4Settings.SIGNING_NAME);
    }

    static String region(Context properties) {
        var region = properties.get(EndpointAuthSchemeSettings.SIGNING_REGION);
        return region != null ? region : properties.expect(RegionSetting.REGION);
    }

    static Clock clock(Context properties) {
        return properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());
    }

    static String regionSet(Context properties, SigningResources r) {
        List<String> set = properties.get(EndpointAuthSchemeSettings.SIGNING_REGION_SET);
        if (set == null || set.isEmpty()) {
            set = properties.get(SigV4aSettings.SIGNING_REGION_SET);
        }
        if (set != null && !set.isEmpty()) {
            return r.regionSetHeaderValue(set);
        }
        return region(properties);
    }

    static boolean isS3(String signingName) {
        return "s3".equals(signingName) || "s3express".equals(signingName);
    }

    static boolean normalizePath(Context properties, boolean isS3) {
        Boolean explicit = properties.get(SigV4Settings.NORMALIZE_PATH);
        return explicit != null ? explicit : !isS3;
    }

    static boolean doubleEncodePath(Context properties, boolean isS3) {
        Boolean disabled = properties.get(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING);
        if (disabled != null) {
            return !disabled;
        }
        Boolean explicit = properties.get(SigV4Settings.DOUBLE_URL_ENCODE);
        return explicit != null ? explicit : !isS3;
    }
}
