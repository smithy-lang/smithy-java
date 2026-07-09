/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * Single-slot handshake between the VT that owns an exchange and the muxer writer thread.
 *
 * <p>The owning VT submits work to the muxer and then blocks in {@link #awaitCompletion()}
 * until the writer thread reports success via {@link #signalSuccess()} or failure via
 * {@link #signalFailure(Throwable)}.
 *
 * <p>Uses lock-free {@link LockSupport} park/unpark rather than a monitor to avoid monitor
 * inflation overhead and to avoid pinning the carrier thread when a VT blocks.
 */
final class H2WriteGate {

    private volatile Thread waitingWriter;
    private volatile boolean completed;
    private volatile Throwable error;

    /**
     * Block until signaled by the writer thread, then check for errors.
     *
     * <p>Called by the VT that owns the exchange after submitting work to the muxer.
     *
     * @throws IOException if a write error occurred
     */
    void awaitCompletion() throws IOException {
        // Fast path: already completed
        if (completed) {
            completed = false;
            checkError();
            return;
        }

        // Register as waiting and park until signaled
        waitingWriter = Thread.currentThread();
        try {
            while (!completed) {
                LockSupport.park();
                if (Thread.interrupted()) {
                    throw new IOException("Interrupted waiting for write completion");
                }
            }
        } finally {
            waitingWriter = null;
        }

        completed = false;
        checkError();
    }

    private void checkError() throws IOException {
        Throwable e = error;
        if (e != null) {
            error = null;
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Write failed", e);
        }
    }

    /**
     * Signal the waiting writer that the write completed successfully.
     *
     * <p>Called by the muxer worker thread after completing a write.
     */
    void signalSuccess() {
        completed = true;
        Thread t = waitingWriter;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Signal the waiting writer that the write failed.
     *
     * <p>Called by the muxer worker thread when a write error occurs.
     *
     * @param cause the error that occurred
     */
    void signalFailure(Throwable cause) {
        error = cause;
        completed = true;
        Thread t = waitingWriter;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }
}
