/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Thread-safe packed state for an HTTP/2 stream.
 *
 * <p>Encapsulates stream state machine, read state, status code, and flags in a single 32-bit integer.
 * All state transitions use CAS operations for thread safety between the reader thread (which delivers
 * response data) and the user's virtual thread (which reads the response).
 *
 * <h2>Bit Layout (32 bits total)</h2>
 * <pre>
 * [0-9]   (10 bits): StatusCode (0-1023). 0 means "not set" (-1 logic)
 * [10]    (1 bit)  : ResponseHeadersReceived
 * [11]    (1 bit)  : EndStreamReceived
 * [12]    (1 bit)  : EndStreamSent
 * [13-15] (3 bits) : ReadState (4 states, capacity for 8)
 * [16-19] (4 bits) : StreamState (5 states, capacity for 16)
 * [20-31] (12 bits): Reserved
 * </pre>
 *
 * <h2>Stream States (RFC 9113 Section 5.1)</h2>
 * <ul>
 *   <li>IDLE: Initial state before HEADERS sent</li>
 *   <li>OPEN: Both sides can send data</li>
 *   <li>HALF_CLOSED_LOCAL: We sent END_STREAM, waiting for response</li>
 *   <li>HALF_CLOSED_REMOTE: They sent END_STREAM, we can still send</li>
 *   <li>CLOSED: Both sides done</li>
 * </ul>
 *
 * <h2>Read States</h2>
 * <ul>
 *   <li>WAITING: Waiting for response headers</li>
 *   <li>READING: Response headers received, reading body</li>
 *   <li>DONE: Response body complete (END_STREAM received)</li>
 *   <li>ERROR: An error occurred</li>
 * </ul>
 */
final class H2StreamState {

    private static final int MASK_STATUS_CODE = 0x3FF; // 10 bits
    private static final int FLAG_HEADERS_RECEIVED = 1 << 10;
    private static final int FLAG_END_STREAM_RX = 1 << 11;
    private static final int FLAG_END_STREAM_TX = 1 << 12;

    // ReadState constants (shift 13, 3 bits)
    private static final int SHIFT_READ_STATE = 13;
    private static final int MASK_READ_STATE = 0x7 << SHIFT_READ_STATE;

    // StreamState constants (shift 16, 4 bits)
    private static final int SHIFT_STREAM_STATE = 16;
    private static final int MASK_STREAM_STATE = 0xF << SHIFT_STREAM_STATE;

    /** Read state: waiting for response headers. */
    static final int RS_WAITING = 0;
    /** Read state: response headers received, reading body. */
    static final int RS_READING = 1;
    /** Read state: response body complete (END_STREAM received). */
    static final int RS_DONE = 2;
    /** Read state: an error occurred. */
    static final int RS_ERROR = 3;
    /** Stream state: initial state before HEADERS sent. */
    static final int SS_IDLE = 0;
    /** Stream state: both sides can send data. */
    static final int SS_OPEN = 1;
    /** Stream state: we sent END_STREAM, waiting for response. */
    static final int SS_HALF_CLOSED_LOCAL = 2;
    /** Stream state: they sent END_STREAM, we can still send. */
    static final int SS_HALF_CLOSED_REMOTE = 3;
    /** Stream state: both sides done. */
    static final int SS_CLOSED = 4;

    /** CAS VarHandle */
    private static final VarHandle STATE_HANDLE;

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup().findVarHandle(H2StreamState.class, "packedState", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Initial: SS_IDLE, RS_WAITING, no flags, status=0
    @SuppressWarnings("FieldMayBeFinal") // it's mutated with a VarHandle
    private volatile int packedState = (SS_IDLE << SHIFT_STREAM_STATE) | (RS_WAITING << SHIFT_READ_STATE);

    /**
     * Get the current read state.
     *
     * @return one of RS_WAITING, RS_READING, RS_DONE, RS_ERROR
     */
    int getReadState() {
        return (packedState & MASK_READ_STATE) >> SHIFT_READ_STATE;
    }

    /**
     * Get the current stream state.
     *
     * @return one of SS_IDLE, SS_OPEN, SS_HALF_CLOSED_LOCAL, SS_HALF_CLOSED_REMOTE, SS_CLOSED
     */
    int getStreamState() {
        return (packedState & MASK_STREAM_STATE) >> SHIFT_STREAM_STATE;
    }

    /**
     * Check if response headers have been received.
     *
     * @return true if response headers (final, not 1xx) have been received
     */
    boolean isResponseHeadersReceived() {
        return (packedState & FLAG_HEADERS_RECEIVED) != 0;
    }

    /**
     * Check if END_STREAM has been received from the remote peer.
     *
     * @return true if END_STREAM was received
     */
    boolean isEndStreamReceived() {
        return (packedState & FLAG_END_STREAM_RX) != 0;
    }

    /**
     * Check if END_STREAM has been sent to the remote peer.
     *
     * @return true if END_STREAM was sent
     */
    boolean isEndStreamSent() {
        return (packedState & FLAG_END_STREAM_TX) != 0;
    }

    /**
     * Get the HTTP status code from the response.
     *
     * @return the status code, or -1 if not yet received
     */
    int getStatusCode() {
        int code = packedState & MASK_STATUS_CODE;
        return code == 0 ? -1 : code; // 0 means not set
    }

    /**
     * Atomically set response headers received with status code.
     * Transitions read state from WAITING to READING if appropriate.
     *
     * @param statusCode the HTTP status code (100-599)
     */
    void setResponseHeadersReceived(int statusCode) {
        for (;;) {
            int current = packedState;
            int newState = current;

            // Set status code (clear old, set new)
            newState &= ~MASK_STATUS_CODE;
            newState |= (statusCode & MASK_STATUS_CODE);

            // Set headers received flag
            newState |= FLAG_HEADERS_RECEIVED;

            // Transition read state: WAITING -> READING
            int readState = (current & MASK_READ_STATE) >> SHIFT_READ_STATE;
            if (readState == RS_WAITING) {
                newState &= ~MASK_READ_STATE;
                newState |= (RS_READING << SHIFT_READ_STATE);
            }

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically mark end stream received.
     * Updates read state to DONE and stream state appropriately.
     */
    void markEndStreamReceived() {
        for (;;) {
            int current = packedState;
            int newState = current | FLAG_END_STREAM_RX;

            // Set read state to DONE
            newState &= ~MASK_READ_STATE;
            newState |= (RS_DONE << SHIFT_READ_STATE);

            // Update stream state
            int currentSS = (current & MASK_STREAM_STATE) >> SHIFT_STREAM_STATE;
            int newSS = computeEndStreamTransition(currentSS, true);
            if (newSS >= 0) {
                newState &= ~MASK_STREAM_STATE;
                newState |= (newSS << SHIFT_STREAM_STATE);
            }

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically mark end stream sent.
     * Updates stream state appropriately.
     */
    void markEndStreamSent() {
        for (;;) {
            int current = packedState;
            if ((current & FLAG_END_STREAM_TX) != 0) {
                return; // Already set
            }

            int newState = current | FLAG_END_STREAM_TX;

            // Update stream state
            int currentSS = (current & MASK_STREAM_STATE) >> SHIFT_STREAM_STATE;
            int newSS = computeEndStreamTransition(currentSS, false);
            if (newSS >= 0) {
                newState &= ~MASK_STREAM_STATE;
                newState |= (newSS << SHIFT_STREAM_STATE);
            }

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically set read state to DONE.
     */
    void setReadStateDone() {
        for (;;) {
            int current = packedState;
            int newState = current;
            newState &= ~MASK_READ_STATE;
            newState |= (RS_DONE << SHIFT_READ_STATE);

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically set stream state to CLOSED.
     */
    void setStreamStateClosed() {
        for (;;) {
            int current = packedState;
            int newState = current;
            newState &= ~MASK_STREAM_STATE;
            newState |= (SS_CLOSED << SHIFT_STREAM_STATE);

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically set error state: endStreamReceived flag + readState=ERROR.
     * Does NOT update stream state (unlike markEndStreamReceived).
     * Used for error paths where we want to signal the consumer without
     * affecting protocol state machine.
     */
    void setErrorState() {
        setEndStreamFlagAndReadState(RS_ERROR);
    }

    /**
     * Atomically set endStreamReceived flag and readState=DONE.
     * Does NOT update stream state - used by enqueueData where we're just
     * recording that we've received all data, but stream state machine
     * transitions happen elsewhere (handleHeadersEvent).
     */
    void setEndStreamReceivedFlag() {
        setEndStreamFlagAndReadState(RS_DONE);
    }

    /**
     * Called when headers are encoded and about to be sent.
     * Atomically transitions stream state and optionally marks end stream sent.
     *
     * @param endStream true if END_STREAM flag is set on the HEADERS frame
     */
    void onHeadersEncoded(boolean endStream) {
        for (;;) {
            int current = packedState;
            int newState = current;

            if (endStream) {
                newState |= FLAG_END_STREAM_TX;
                newState &= ~MASK_STREAM_STATE;
                newState |= (SS_HALF_CLOSED_LOCAL << SHIFT_STREAM_STATE);
            } else {
                newState &= ~MASK_STREAM_STATE;
                newState |= (SS_OPEN << SHIFT_STREAM_STATE);
            }

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Atomically set endStreamReceived flag and the specified read state.
     * Does NOT update stream state - used where we want to signal the consumer
     * without affecting the H2 protocol state machine.
     *
     * @param readState the read state to set (RS_DONE or RS_ERROR)
     */
    private void setEndStreamFlagAndReadState(int readState) {
        for (;;) {
            int current = packedState;
            int newState = current | FLAG_END_STREAM_RX;
            newState &= ~MASK_READ_STATE;
            newState |= (readState << SHIFT_READ_STATE);

            if (STATE_HANDLE.compareAndSet(this, current, newState)) {
                return;
            }
        }
    }

    /**
     * Compute the new stream state after an end-stream event.
     *
     * @param currentStreamState current stream state
     * @param isReceived true if end-stream received, false if end-stream sent
     * @return the new stream state, or -1 if no change needed
     */
    private static int computeEndStreamTransition(int currentStreamState, boolean isReceived) {
        if (isReceived) {
            // End stream received: OPEN→HALF_CLOSED_REMOTE, HALF_CLOSED_LOCAL→CLOSED
            if (currentStreamState == SS_OPEN) {
                return SS_HALF_CLOSED_REMOTE;
            } else if (currentStreamState == SS_HALF_CLOSED_LOCAL) {
                return SS_CLOSED;
            }
        } else {
            // End stream sent: OPEN→HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE→CLOSED
            if (currentStreamState == SS_OPEN) {
                return SS_HALF_CLOSED_LOCAL;
            } else if (currentStreamState == SS_HALF_CLOSED_REMOTE) {
                return SS_CLOSED;
            }
        }
        return -1; // No change
    }
}
