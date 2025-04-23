/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * A factory for creating {@link ClientPlugin} instances from a service bundle.
 */
public interface BundleClientPluginProvider {
    /**
     * Creates a new ClientPlugin that can be used to configure the client.
     *
     * @return a new ClientPlugin
     */
    ClientPlugin newPlugin();
}
