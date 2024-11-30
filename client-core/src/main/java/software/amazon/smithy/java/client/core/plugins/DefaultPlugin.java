/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.function.Predicate;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * Applies default plugins to a client.
 *
 * <p>This plugin is applied to a client by default. You can prevent it from being applied using
 * {@link Client.Builder#addPluginPredicate(Predicate)} and filtering this plugin by class.
 */
public final class DefaultPlugin implements ClientPlugin {

    public static final DefaultPlugin INSTANCE = new DefaultPlugin();

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.applyPlugin(ApplyModelRetryInfoPlugin.INSTANCE);
        config.applyPlugin(InjectIdempotencyTokenPlugin.INSTANCE);
    }
}
