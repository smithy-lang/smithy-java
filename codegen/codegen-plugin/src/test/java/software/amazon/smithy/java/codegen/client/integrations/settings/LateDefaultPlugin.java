/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.settings;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * Test plugin registered as a default plugin from {@code JavaCodegenIntegration#customize}, which
 * runs <em>after</em> the client builder has already been generated and therefore has no effect.
 */
public final class LateDefaultPlugin implements ClientPlugin {
    @Override
    public void configureClient(ClientConfig.Builder config) {
        // no-op
    }
}
