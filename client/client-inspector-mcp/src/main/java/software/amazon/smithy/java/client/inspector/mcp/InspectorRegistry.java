/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

/**
 * Holds the process-wide {@link InspectorState} shared between the flag-gated {@link InspectorPlugin}
 * and an MCP launcher, so an eval harness can enable instrumentation with a single flag and still
 * reach the same state to expose over MCP.
 */
public final class InspectorRegistry {

    private static volatile InspectorState shared;

    private InspectorRegistry() {}

    /** Get (creating on first use) the shared inspector state. */
    public static InspectorState shared() {
        var local = shared;
        if (local == null) {
            synchronized (InspectorRegistry.class) {
                local = shared;
                if (local == null) {
                    local = new InspectorState();
                    shared = local;
                }
            }
        }
        return local;
    }
}
