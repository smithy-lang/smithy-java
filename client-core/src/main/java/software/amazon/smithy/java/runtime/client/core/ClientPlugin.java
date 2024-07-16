/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

/**
 * A plugin modifies a client's configuration when the client is created or at request execution time.
 */
@FunctionalInterface
public interface ClientPlugin {

    /**
     * Modify the provided client configuration.
     */
    void configureClient(ClientConfig.Builder config);
}
