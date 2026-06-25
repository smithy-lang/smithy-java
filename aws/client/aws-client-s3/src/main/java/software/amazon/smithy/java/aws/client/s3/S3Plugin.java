/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.s3;

import java.util.ServiceLoader;
import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Wires up S3-specific client behavior that the generic protocol and rules-engine machinery cannot
 * infer from the model alone.
 *
 * <p>Currently this registers {@link S3VirtualHostInterceptor}, which removes the redundant bucket
 * segment from request paths so virtual-hosted-style addressing does not duplicate the bucket (once
 * in the host, once in the path). See that class for the full explanation.
 *
 * <p><strong>Activation.</strong> Discovered via {@link ServiceLoader} as an {@link AutoClientPlugin}
 * and applied in {@link Phase#DEFAULTS}, but only when the client's service is Amazon S3 — detected by
 * the {@code aws.api#service} trait's {@code endpointPrefix} (or {@code arnNamespace}) being {@code s3}.
 * For every other service it is inert, so it is safe to keep on the classpath of a multi-service tool
 * such as a dynamic client. No opt-in configuration is required.
 */
public final class S3Plugin implements AutoClientPlugin {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(S3Plugin.class);
    private static final TraitKey<ServiceTrait> SERVICE_TRAIT = TraitKey.get(ServiceTrait.class);

    @Override
    public Phase getPluginPhase() {
        return Phase.DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (!isS3(config)) {
            return;
        }
        if (!config.interceptors().contains(S3VirtualHostInterceptor.INSTANCE)) {
            LOGGER.debug("Applying S3 virtual-host addressing interceptor");
            config.addInterceptor(S3VirtualHostInterceptor.INSTANCE);
        }
    }

    private static boolean isS3(ClientConfig.Builder config) {
        var service = config.service();
        if (service == null) {
            return false;
        }
        var trait = service.schema().getTrait(SERVICE_TRAIT);
        if (trait == null) {
            return false;
        }
        // endpointPrefix is the canonical signal; arnNamespace is the fallback (both are "s3" for S3).
        return "s3".equals(trait.getEndpointPrefix()) || "s3".equals(trait.getArnNamespace());
    }
}
