/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.retries.api.RetryInfo;

/**
 * Applies default plugins to a client.
 *
 * <p>This plugin is applied to a client automatically. It will set {@link RetryInfo} on exceptions based on
 * modeled information, and will automatically inject idempotency tokens when they're not provided.
 */
public final class DefaultPlugin implements ClientPlugin {

    public static final DefaultPlugin INSTANCE = new DefaultPlugin();

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.applyPlugin(ApplyModelRetryInfoPlugin.INSTANCE);
        config.applyPlugin(InjectIdempotencyTokenPlugin.INSTANCE);
    }
}
