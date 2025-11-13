/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * Detects and applies auto-client plugins that implement the {@link AutoClientPlugin} SPI.
 */
public final class AutoPlugin implements ClientPlugin {

    public static final AutoPlugin INSTANCE = new AutoPlugin();

    private static final List<ClientPlugin> CHILDREN;

    static {
        List<ClientPlugin> children = new ArrayList<>();
        for (var plugin : ServiceLoader.load(AutoClientPlugin.class)) {
            children.add(plugin);
        }
        CHILDREN = Collections.unmodifiableList(children);
    }

    private AutoPlugin() {}

    @Override
    public List<ClientPlugin> getChildPlugins() {
        return CHILDREN;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {}
}
