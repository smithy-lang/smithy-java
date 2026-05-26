/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.settings;

import java.util.List;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Typed property keys for {@code EndpointAuthScheme} entries produced from the {@code authSchemes}
 * property of an Endpoints 2.0 rule set.
 *
 * <p>The client pipeline merges these onto the signer's properties. Signers SHOULD prefer these
 * override values over their own scheme-level keys (e.g. {@code SigV4Settings.SIGNING_NAME}).
 *
 * <p>This is a deprecated compat mechanism kept alive for the four services that depend on it
 * (s3, ses, eventbridge, cloudfront-keyvaluestore); new services should use a custom auth-scheme
 * resolver instead.
 */
@SmithyUnstableApi
public final class EndpointAuthSchemeSettings {

    /**
     * Service signing name to use for this endpoint. For example, {@code "s3"} or {@code "s3express"}.
     */
    public static final Context.Key<String> SIGNING_NAME = Context.key("Endpoint signingName override");

    /**
     * Region to use when signing requests for this endpoint.
     */
    public static final Context.Key<String> SIGNING_REGION = Context.key("Endpoint signingRegion override");

    /**
     * Set of signing regions for SigV4a multi-region endpoints.
     */
    public static final Context.Key<List<String>> SIGNING_REGION_SET =
            Context.key("Endpoint signingRegionSet override (sigv4a)");

    /**
     * If {@code true}, do not double-escape the path segment during signing (s3v4 behavior).
     */
    public static final Context.Key<Boolean> DISABLE_DOUBLE_ENCODING =
            Context.key("Endpoint disableDoubleEncoding override");

    private EndpointAuthSchemeSettings() {}
}
