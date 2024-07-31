/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;

public class NettyUtils {

    private NettyUtils() {
    }

    static CompletableFuture<Void> toVoidCompletableFuture(Future<?> future) {
        var cf = new CompletableFuture<Void>();
        future.addListener(f -> {
            if (f.isSuccess()) {
                cf.complete(null);
            } else {
                cf.completeExceptionally(f.cause());
            }
        });
        return cf;
    }
}
