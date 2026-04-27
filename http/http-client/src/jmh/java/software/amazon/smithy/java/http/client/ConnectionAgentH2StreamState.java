/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Port of the production H2 packed stream state for the connection-agent experiment.
 */
final class ConnectionAgentH2StreamState {

    private static final int MASK_STATUS_CODE = 0x3FF;
    private static final int FLAG_HEADERS_RECEIVED = 1 << 10;
    private static final int FLAG_END_STREAM_RX = 1 << 11;
    private static final int FLAG_END_STREAM_TX = 1 << 12;

    private static final int SHIFT_READ_STATE = 13;
    private static final int MASK_READ_STATE = 0x7 << SHIFT_READ_STATE;

    private static final int SHIFT_STREAM_STATE = 16;
    private static final int MASK_STREAM_STATE = 0xF << SHIFT_STREAM_STATE;

    static final int RS_WAITING = 0;
    static final int RS_READING = 1;
    static final int RS_DONE = 2;
    static final int RS_ERROR = 3;

    static final int SS_IDLE = 0;
    static final int SS_OPEN = 1;
    static final int SS_HALF_CLOSED_LOCAL = 2;
    static final int SS_HALF_CLOSED_REMOTE = 3;
    static final int SS_CLOSED = 4;

    private static final VarHandle STATE_HANDLE;

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup()
                    .findVarHandle(ConnectionAgentH2StreamState.class, "packedState", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int packedState = (SS_IDLE << SHIFT_STREAM_STATE) | (RS_WAITING << SHIFT_READ_STATE);

    int getStatusCode() {
        int code = packedState & MASK_STATUS_CODE;
        return code == 0 ? -1 : code;
    }

    boolean isResponseHeadersReceived() {
        return (packedState & FLAG_HEADERS_RECEIVED) != 0;
    }

    boolean isEndStreamReceived() {
        return (packedState & FLAG_END_STREAM_RX) != 0;
    }

    boolean isEndStreamSent() {
        return (packedState & FLAG_END_STREAM_TX) != 0;
    }

    void onHeadersEncoded(boolean endStream) {
        for (;;) {
            int current = packedState;
            int next = current;
            if (endStream) {
                next |= FLAG_END_STREAM_TX;
                next &= ~MASK_STREAM_STATE;
                next |= (SS_HALF_CLOSED_LOCAL << SHIFT_STREAM_STATE);
            } else {
                next &= ~MASK_STREAM_STATE;
                next |= (SS_OPEN << SHIFT_STREAM_STATE);
            }
            if (STATE_HANDLE.compareAndSet(this, current, next)) {
                return;
            }
        }
    }

    void setResponseHeadersReceived(int statusCode) {
        for (;;) {
            int current = packedState;
            int next = current;
            next &= ~MASK_STATUS_CODE;
            next |= (statusCode & MASK_STATUS_CODE);
            next |= FLAG_HEADERS_RECEIVED;
            int readState = (current & MASK_READ_STATE) >> SHIFT_READ_STATE;
            if (readState == RS_WAITING) {
                next &= ~MASK_READ_STATE;
                next |= (RS_READING << SHIFT_READ_STATE);
            }
            if (STATE_HANDLE.compareAndSet(this, current, next)) {
                return;
            }
        }
    }

    void markEndStreamReceived() {
        for (;;) {
            int current = packedState;
            int next = current | FLAG_END_STREAM_RX;
            next &= ~MASK_READ_STATE;
            next |= (RS_DONE << SHIFT_READ_STATE);
            int currentStreamState = (current & MASK_STREAM_STATE) >> SHIFT_STREAM_STATE;
            int nextStreamState = computeEndStreamTransition(currentStreamState, true);
            if (nextStreamState >= 0) {
                next &= ~MASK_STREAM_STATE;
                next |= (nextStreamState << SHIFT_STREAM_STATE);
            }
            if (STATE_HANDLE.compareAndSet(this, current, next)) {
                return;
            }
        }
    }

    void markEndStreamSent() {
        for (;;) {
            int current = packedState;
            if ((current & FLAG_END_STREAM_TX) != 0) {
                return;
            }
            int next = current | FLAG_END_STREAM_TX;
            int currentStreamState = (current & MASK_STREAM_STATE) >> SHIFT_STREAM_STATE;
            int nextStreamState = computeEndStreamTransition(currentStreamState, false);
            if (nextStreamState >= 0) {
                next &= ~MASK_STREAM_STATE;
                next |= (nextStreamState << SHIFT_STREAM_STATE);
            }
            if (STATE_HANDLE.compareAndSet(this, current, next)) {
                return;
            }
        }
    }

    void setErrorState() {
        setEndStreamFlagAndReadState(RS_ERROR);
    }

    private void setEndStreamFlagAndReadState(int readState) {
        for (;;) {
            int current = packedState;
            int next = current | FLAG_END_STREAM_RX;
            next &= ~MASK_READ_STATE;
            next |= (readState << SHIFT_READ_STATE);
            if (STATE_HANDLE.compareAndSet(this, current, next)) {
                return;
            }
        }
    }

    private static int computeEndStreamTransition(int currentStreamState, boolean isReceived) {
        if (isReceived) {
            if (currentStreamState == SS_OPEN) {
                return SS_HALF_CLOSED_REMOTE;
            } else if (currentStreamState == SS_HALF_CLOSED_LOCAL) {
                return SS_CLOSED;
            }
        } else {
            if (currentStreamState == SS_OPEN) {
                return SS_HALF_CLOSED_LOCAL;
            } else if (currentStreamState == SS_HALF_CLOSED_REMOTE) {
                return SS_CLOSED;
            }
        }
        return -1;
    }
}
