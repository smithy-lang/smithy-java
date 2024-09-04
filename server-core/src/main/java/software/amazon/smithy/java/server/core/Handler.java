/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;

public interface Handler {

    CompletableFuture<Void> before(Job job);

    CompletableFuture<Void> after(Job job);
}
