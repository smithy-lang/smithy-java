/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class H2StreamStateTest {

    @Test
    void initialStateIsCorrect() {
        var state = new H2StreamState();

        assertFalse(state.isResponseHeadersReceived());
        assertFalse(state.isEndStreamSent());
        assertFalse(state.isEndStreamReceived());
        assertEquals(H2StreamState.RS_WAITING, state.getReadState());
    }

    @Test
    void setResponseHeadersReceivedTransitionsState() {
        var state = new H2StreamState();
        state.setResponseHeadersReceived(200);

        assertTrue(state.isResponseHeadersReceived());
        assertEquals(200, state.getStatusCode());
        assertEquals(H2StreamState.RS_READING, state.getReadState());
    }

    @Test
    void markEndStreamSentSetsFlag() {
        var state = new H2StreamState();
        state.markEndStreamSent();

        assertTrue(state.isEndStreamSent());
    }

    @Test
    void markEndStreamReceivedSetsReadStateDone() {
        var state = new H2StreamState();
        state.setResponseHeadersReceived(200);
        state.markEndStreamReceived();

        assertTrue(state.isEndStreamReceived());
        assertEquals(H2StreamState.RS_DONE, state.getReadState());
    }

    @Test
    void setErrorStateSetsReadStateError() {
        var state = new H2StreamState();
        state.setErrorState();

        assertEquals(H2StreamState.RS_ERROR, state.getReadState());
    }

    @Test
    void streamStateTransitionsCorrectly() {
        var state = new H2StreamState();

        // Initial: IDLE
        assertEquals(H2StreamState.SS_IDLE, state.getStreamState());

        // After headers encoded without endStream -> OPEN
        state.onHeadersEncoded(false);
        assertEquals(H2StreamState.SS_OPEN, state.getStreamState());

        // After headers encoded with endStream -> HALF_CLOSED_LOCAL
        var state2 = new H2StreamState();
        state2.onHeadersEncoded(true);
        assertEquals(H2StreamState.SS_HALF_CLOSED_LOCAL, state2.getStreamState());
    }

    @Test
    void halfClosedLocalToClosedOnEndStreamReceived() {
        var state = new H2StreamState();
        state.onHeadersEncoded(true); // IDLE -> HALF_CLOSED_LOCAL
        state.setResponseHeadersReceived(200);

        state.markEndStreamReceived();

        assertEquals(H2StreamState.SS_CLOSED, state.getStreamState());
    }

    @Test
    void halfClosedRemoteToClosedOnEndStreamSent() {
        var state = new H2StreamState();
        state.onHeadersEncoded(false); // IDLE -> OPEN
        state.setResponseHeadersReceived(200);
        state.markEndStreamReceived(); // OPEN -> HALF_CLOSED_REMOTE

        assertEquals(H2StreamState.SS_HALF_CLOSED_REMOTE, state.getStreamState());

        state.markEndStreamSent(); // HALF_CLOSED_REMOTE -> CLOSED

        assertEquals(H2StreamState.SS_CLOSED, state.getStreamState());
    }

    @Test
    void setStreamStateClosedWorks() {
        var state = new H2StreamState();
        state.setStreamStateClosed();

        assertEquals(H2StreamState.SS_CLOSED, state.getStreamState());
    }

    @Test
    void setReadStateDoneWorks() {
        var state = new H2StreamState();
        state.setReadStateDone();

        assertEquals(H2StreamState.RS_DONE, state.getReadState());
    }

    @Test
    void setEndStreamReceivedFlagOnlySetsFlag() {
        var state = new H2StreamState();
        state.setResponseHeadersReceived(200);

        state.setEndStreamReceivedFlag();

        assertTrue(state.isEndStreamReceived());
        assertEquals(H2StreamState.RS_DONE, state.getReadState());
        // Stream state should NOT change (unlike markEndStreamReceived)
    }
}
