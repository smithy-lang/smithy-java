/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;

/**
 * Resolved STS endpoint configuration: which region to use and whether to use
 * the legacy global endpoint instead of the regional endpoint.
 *
 * <p>Region resolution priority (highest first):
 * <ol>
 *   <li>The region attached to the credential source (profile-level
 *       {@code region} carried on {@code AssumeRole} / {@code WebIdentityToken}).</li>
 *   <li>{@code AWS_REGION} env var.</li>
 *   <li>{@code AWS_DEFAULT_REGION} env var.</li>
 *   <li>The active profile's {@code region} property.</li>
 * </ol>
 *
 * <p>Regional vs legacy resolution priority (highest first):
 * <ol>
 *   <li>{@code AWS_STS_REGIONAL_ENDPOINTS} env var.</li>
 *   <li>The active profile's {@code sts_regional_endpoints} property.</li>
 *   <li>Default: {@code regional}.</li>
 * </ol>
 *
 * <p>If no region resolves at all, falls back to {@code us-east-1} with the global endpoint
 * enabled, matching SDK v2 behavior for STS when no region is configured.
 *
 * @see <a href="https://docs.aws.amazon.com/sdkref/latest/guide/feature-sts-regionalized-endpoints.html">
 *     STS regionalized endpoints</a>
 */
record StsEndpointConfig(String region, boolean useGlobalEndpoint) {

    static StsEndpointConfig resolve(String sourceRegion, ChainSetup setup) {
        String region = sourceRegion;
        if (region == null) {
            region = setup.getenv("AWS_REGION");
        }
        if (region == null) {
            region = setup.getenv("AWS_DEFAULT_REGION");
        }
        if (region == null && setup.profile() != null) {
            region = setup.profile().property("region");
        }

        String mode = setup.getenv("AWS_STS_REGIONAL_ENDPOINTS");
        if (mode == null && setup.profile() != null) {
            mode = setup.profile().property("sts_regional_endpoints");
        }

        if (region == null) {
            return new StsEndpointConfig("us-east-1", true);
        }
        return new StsEndpointConfig(region, "legacy".equalsIgnoreCase(mode));
    }
}
