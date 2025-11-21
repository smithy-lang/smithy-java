/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.retries.api.RetryInfo;

/**
 * Applies default plugins to a client.
 *
 * <p>This plugin is applied to a client automatically. It sets {@link RetryInfo} on exceptions based on
 * modeled information, and will automatically inject idempotency tokens when they're not provided.
 */
public final class DefaultPlugin implements ClientPlugin {

    public static final DefaultPlugin INSTANCE = new DefaultPlugin();

    private static final List<ClientPlugin> CHILDREN = Arrays.asList(
            DiscoverTransportPlugin.INSTANCE,
            ApplyModelRetryInfoPlugin.INSTANCE,
            InjectIdempotencyTokenPlugin.INSTANCE,
            AutoPlugin.INSTANCE);

    private DefaultPlugin() {}

    @Override
    public Phase getPluginPhase() {
        return Phase.FIRST;
    }

    @Override
    public List<ClientPlugin> getChildPlugins() {
        return CHILDREN;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {}
}
