/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Work items processed by the writer thread.
 * Base class includes deadlineTick for watchdog-based timeout, eliminating
 * the need for a separate TimedWorkItem wrapper allocation.
 */
abstract sealed class H2MuxerWorkItem {
    /**
     * Deadline tick for timeout (0 = no timeout). Set before enqueueing.
     */
    int deadlineTick;

    static final class EncodeHeaders extends H2MuxerWorkItem {
        final HttpRequest request;
        final H2Exchange exchange;
        final boolean endStream;

        EncodeHeaders(HttpRequest request, H2Exchange exchange, boolean endStream) {
            this.request = request;
            this.exchange = exchange;
            this.endStream = endStream;
        }
    }

    static final class WriteTrailers extends H2MuxerWorkItem {
        final int streamId;
        final HttpHeaders trailers;

        WriteTrailers(int streamId, HttpHeaders trailers) {
            this.streamId = streamId;
            this.trailers = trailers;
        }
    }

    static final class WriteRst extends H2MuxerWorkItem {
        final int streamId;
        final int errorCode;

        WriteRst(int streamId, int errorCode) {
            this.streamId = streamId;
            this.errorCode = errorCode;
        }
    }

    static final class WriteGoaway extends H2MuxerWorkItem {
        final int lastStreamId;
        final int errorCode;
        final String debugData;

        WriteGoaway(int lastStreamId, int errorCode, String debugData) {
            this.lastStreamId = lastStreamId;
            this.errorCode = errorCode;
            this.debugData = debugData;
        }
    }

    static final class WriteWindowUpdate extends H2MuxerWorkItem {
        final int streamId;
        final int increment;

        WriteWindowUpdate(int streamId, int increment) {
            this.streamId = streamId;
            this.increment = increment;
        }
    }

    static final class WriteSettingsAck extends H2MuxerWorkItem {
        static final WriteSettingsAck INSTANCE = new WriteSettingsAck();
    }

    static final class WritePing extends H2MuxerWorkItem {
        final byte[] payload;
        final boolean ack;

        WritePing(byte[] payload, boolean ack) {
            this.payload = payload;
            this.ack = ack;
        }
    }

    static final class Shutdown extends H2MuxerWorkItem {
        static final Shutdown INSTANCE = new Shutdown();
    }

    static final class CheckDataQueue extends H2MuxerWorkItem {
        static final CheckDataQueue INSTANCE = new CheckDataQueue();
    }
}
