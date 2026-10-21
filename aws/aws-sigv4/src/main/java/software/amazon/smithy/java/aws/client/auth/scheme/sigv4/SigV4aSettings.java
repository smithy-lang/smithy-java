/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Client settings for SigV4a signing.
 *
 * <p>SigV4a uses {@link SigV4Settings} and adds the optional {@link #SIGNING_REGION_SET}.
 * See {@link SigV4aAuthScheme} for region set precedence.
 */
public interface SigV4aSettings<B extends ClientSetting<B>> extends SigV4Settings<B> {
    /**
     * Regions where a SigV4a signature is valid, in the order sent in {@code X-Amz-Region-Set}.
     *
     * <p>A single {@code "*"} entry means all regions.
     */
    Context.Key<List<String>> SIGNING_REGION_SET =
            Context.key("SigV4a signing region set, for example [\"*\"] or [\"us-east-1\", \"us-west-2\"].");

    /**
     * Set the regions SigV4a signatures are valid for.
     *
     * @param regions one or more region names, or the single wildcard {@code "*"}.
     */
    default B signingRegionSet(String... regions) {
        return signingRegionSet(regions == null ? null : Arrays.asList(regions));
    }

    /**
     * Set the regions SigV4a signatures are valid for.
     *
     * @param regions one or more region names, or the single wildcard {@code "*"}.
     */
    default B signingRegionSet(List<String> regions) {
        return putConfig(SIGNING_REGION_SET, SigV4aSigner.normalizeRegionSet(regions));
    }
}
