/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.nio.ByteBuffer;

/**
 * A pending DATA frame write queued for the writer thread.
 *
 * <p>Mutable to allow reuse. The buffer is in read mode (ready for writing to socket).
 */
final class PendingWrite {
    ByteBuffer data;
    int flags;
    boolean borrowed; // true if data came from pool and should be returned

    /**
     * Initialize with a pooled buffer.
     */
    PendingWrite init(ByteBuffer data, int flags) {
        this.data = data;
        this.flags = flags;
        this.borrowed = true;
        return this;
    }

    /**
     * Initialize with a non-pooled buffer (caller manages lifecycle).
     */
    PendingWrite initDirect(ByteBuffer data, int flags) {
        this.data = data;
        this.flags = flags;
        this.borrowed = false;
        return this;
    }

    void reset() {
        this.data = null;
        this.flags = 0;
        this.borrowed = false;
    }
}
