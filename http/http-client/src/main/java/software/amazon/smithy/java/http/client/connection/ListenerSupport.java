/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import software.amazon.smithy.java.logging.InternalLogger;

final class ListenerSupport {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ListenerSupport.class);

    private ListenerSupport() {}

    static void listenerFailed(String event, Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        LOGGER.warn("HTTP client listener failed in {}", event, error);
    }
}
