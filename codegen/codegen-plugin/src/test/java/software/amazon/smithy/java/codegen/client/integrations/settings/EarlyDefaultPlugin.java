/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.settings;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * Test plugin registered as a default plugin from
 * {@code JavaCodegenIntegration#customizeSettings} (which runs early enough to take effect).
 */
public final class EarlyDefaultPlugin implements ClientPlugin {
    @Override
    public void configureClient(ClientConfig.Builder config) {
        // no-op
    }
}
