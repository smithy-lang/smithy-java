/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;

public final class ProtocolHandler implements Handler {

    @Override
    public CompletableFuture<Void> before(Job job) {
        return job.chosenProtocol().deserializeInput(job);
    }

    @Override
    public CompletableFuture<Void> after(Job job) {
        return job.chosenProtocol().serializeOutput(job);
    }
}
