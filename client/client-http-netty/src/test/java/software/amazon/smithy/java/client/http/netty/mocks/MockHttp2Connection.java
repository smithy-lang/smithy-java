/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.mocks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FlowController;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamVisitor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class MockHttp2Connection implements Http2Connection {

    private final MockHttp2LocalFlowController localFlowController;
    private final MockLocalEndpoint localEndpoint;

    public MockHttp2Connection(boolean incrementWindowSizeThrows) {
        this.localFlowController = new MockHttp2LocalFlowController(incrementWindowSizeThrows);
        this.localEndpoint = new MockLocalEndpoint(localFlowController);
    }

    public MockHttp2Connection() {
        this(false);
    }

    public Integer initialWindowSizeSet() {
        return localFlowController.initialWindowSize;
    }

    @Override
    public Future<Void> close(Promise<Void> promise) {
        return null;
    }

    @Override
    public PropertyKey newKey() {
        return null;
    }

    @Override
    public void addListener(Listener listener) {

    }

    @Override
    public void removeListener(Listener listener) {

    }

    @Override
    public Http2Stream stream(int streamId) {
        return null;
    }

    @Override
    public boolean streamMayHaveExisted(int streamId) {
        return false;
    }

    @Override
    public Http2Stream connectionStream() {
        return null;
    }

    @Override
    public int numActiveStreams() {
        return 0;
    }

    @Override
    public Http2Stream forEachActiveStream(Http2StreamVisitor visitor) throws Http2Exception {
        return null;
    }

    @Override
    public boolean isServer() {
        return false;
    }

    @Override
    public Endpoint<Http2LocalFlowController> local() {
        return localEndpoint;
    }

    @Override
    public Endpoint<Http2RemoteFlowController> remote() {
        return null;
    }

    @Override
    public boolean goAwayReceived() {
        return false;
    }

    @Override
    public void goAwayReceived(int lastKnownStream, long errorCode, ByteBuf message) throws Http2Exception {

    }

    @Override
    public boolean goAwaySent() {
        return false;
    }

    @Override
    public boolean goAwaySent(int lastKnownStream, long errorCode, ByteBuf message) throws Http2Exception {
        return false;
    }

    static class MockHttp2Stream implements Http2Stream {

        @Override
        public int id() {
            return 0;
        }

        @Override
        public State state() {
            return State.OPEN;
        }

        @Override
        public Http2Stream open(boolean halfClosed) throws Http2Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream closeLocalSide() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream closeRemoteSide() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isResetSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream resetSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> V setProperty(PropertyKey key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> V getProperty(PropertyKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> V removeProperty(PropertyKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream headersSent(boolean isInformational) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHeadersSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTrailersSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream headersReceived(boolean isInformational) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHeadersReceived() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTrailersReceived() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream pushPromiseSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPushPromiseSent() {
            throw new UnsupportedOperationException();
        }
    }

    static class MockLocalEndpoint implements Endpoint<Http2LocalFlowController> {
        private final MockHttp2LocalFlowController localFlowController;

        MockLocalEndpoint(MockHttp2LocalFlowController localFlowController) {
            this.localFlowController = localFlowController;
        }

        @Override
        public int incrementAndGetNextStreamId() {
            return 0;
        }

        @Override
        public boolean isValidStreamId(int streamId) {
            return false;
        }

        @Override
        public boolean mayHaveCreatedStream(int streamId) {
            return false;
        }

        @Override
        public boolean created(Http2Stream stream) {
            return false;
        }

        @Override
        public boolean canOpenStream() {
            return false;
        }

        @Override
        public Http2Stream createStream(int streamId, boolean halfClosed) throws Http2Exception {
            return null;
        }

        @Override
        public Http2Stream reservePushStream(int streamId, Http2Stream parent) throws Http2Exception {
            return null;
        }

        @Override
        public boolean isServer() {
            return false;
        }

        @Override
        public void allowPushTo(boolean allow) {

        }

        @Override
        public boolean allowPushTo() {
            return false;
        }

        @Override
        public int numActiveStreams() {
            return 0;
        }

        @Override
        public int maxActiveStreams() {
            return 0;
        }

        @Override
        public void maxActiveStreams(int maxActiveStreams) {

        }

        @Override
        public int lastStreamCreated() {
            return 0;
        }

        @Override
        public int lastStreamKnownByPeer() {
            return 0;
        }

        @Override
        public Http2LocalFlowController flowController() {
            return localFlowController;
        }

        @Override
        public void flowController(Http2LocalFlowController flowController) {}

        @Override
        public Endpoint<? extends Http2FlowController> opposite() {
            return null;
        }
    }

    static class MockHttp2LocalFlowController implements Http2LocalFlowController {
        private boolean incrementWindowSizeThrows;
        private Integer initialWindowSize;

        public MockHttp2LocalFlowController(boolean incrementWindowSizeThrows) {
            this.incrementWindowSizeThrows = incrementWindowSizeThrows;
        }

        @Override
        public Http2LocalFlowController frameWriter(Http2FrameWriter frameWriter) {
            return null;
        }

        @Override
        public void receiveFlowControlledFrame(Http2Stream stream, ByteBuf data, int padding, boolean endOfStream)
                throws Http2Exception {

        }

        @Override
        public boolean consumeBytes(Http2Stream stream, int numBytes) throws Http2Exception {
            return false;
        }

        @Override
        public int unconsumedBytes(Http2Stream stream) {
            return 0;
        }

        @Override
        public int initialWindowSize(Http2Stream stream) {
            return 0;
        }

        @Override
        public void channelHandlerContext(ChannelHandlerContext ctx) throws Http2Exception {

        }

        @Override
        public void initialWindowSize(int newWindowSize) throws Http2Exception {
            this.initialWindowSize = newWindowSize;
        }

        @Override
        public int initialWindowSize() {
            return 0;
        }

        @Override
        public int windowSize(Http2Stream stream) {
            return 0;
        }

        @Override
        public void incrementWindowSize(Http2Stream stream, int delta) throws Http2Exception {
            if (incrementWindowSizeThrows) {
                throw new Http2Exception(Http2Error.INTERNAL_ERROR, "Internal error");
            }
            this.initialWindowSize = delta;
        }
    }
}
