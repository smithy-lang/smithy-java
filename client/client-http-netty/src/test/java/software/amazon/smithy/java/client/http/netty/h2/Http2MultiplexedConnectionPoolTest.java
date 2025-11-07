/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.CHANNEL_POOL;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CHANNEL;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CONNECTION_POOL;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2StreamChannel;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.http.netty.NettyConstants;
import software.amazon.smithy.java.client.http.netty.mocks.MockChannel;
import software.amazon.smithy.java.client.http.netty.mocks.MockChannelPool;
import software.amazon.smithy.java.client.http.netty.mocks.MockHttp2Connection;
import software.amazon.smithy.java.http.api.HttpVersion;

class Http2MultiplexedConnectionPoolTest {
    private static final int EXPECTED_INITIAL_WINDOW_SIZE = 1031;
    private EventLoopGroup eventLoopGroup;

    @BeforeEach
    public void setup() {
        eventLoopGroup = new NioEventLoopGroup(2);
    }

    @AfterEach
    public void teardown() {
        eventLoopGroup.shutdownGracefully();
    }

    @Test
    public void succeedsAndSetsAttributesAndHandlers() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes successfully
        assertTrue(resultPromise.await(1_000));
        assertTrue(resultPromise.isSuccess());
        assertEquals(mockStream, resultPromise.get());

        // assert initial window size was set to the expected value
        var http2Connection = (MockHttp2Connection) mockParentChannel.attr(NettyHttp2Constants.HTTP2_CONNECTION).get();
        assertNotNull(http2Connection);
        assertNotNull(http2Connection.initialWindowSizeSet());
        assertEquals(EXPECTED_INITIAL_WINDOW_SIZE, http2Connection.initialWindowSizeSet());

        // assert parent attributes
        assertEquals(connectionPool, mockParentChannel.attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).get());
        assertNotNull(mockParentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get());

        // assert parent was not closed
        assertFalse(mockParentChannel.wasExplicitlyClosed());

        // assert stream pipeline
        var mockParentPipeline = mockParentChannel.mockPipeline();
        assertTrue(mockParentPipeline.containsInOrder(ReleaseOnExceptionHandler.class));

        // assert stream attributes
        assertEquals(connectionPool, mockStream.attr(CHANNEL_POOL).get());

        // assert stream pipeline
        var mockStreamPipeline = mockStream.mockPipeline();
        assertTrue(mockStreamPipeline.containsInOrder(
                Http2ToHttpInboundAdapter.class,
                HttpToHttp2OutboundAdapter.class,
                Http2StreamExceptionHandler.class));

        // assert a multiplexed channel was added to the pool
        assertEquals(1, connectionPool.parentCount(), "A multiplexed channel was added to the pool");
    }

    @Test
    public void reusesExistingConnectionIfStreamsAreAvailable() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        Http2StreamBootstrap bootstrap = (ch) -> {
            var mockStream = MockChannel.builder()
                    .parent(mockParentChannel)
                    .buildStream();
            return eventLoopGroup.next().newSucceededFuture(mockStream);
        };

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                bootstrap,
                MultiplexedChannel::new);

        // Set up first promise
        var firstStream = eventLoopGroup.next().<Channel>newPromise();
        var secondStream = eventLoopGroup.next().<Channel>newPromise();

        // -- Act
        // Acquire first stream
        connectionPool.acquire(firstStream);

        // Acquire second stream
        connectionPool.acquire(secondStream);

        // --- Assert

        // assert that the first stream is acquired successfully.
        assertTrue(firstStream.await(1_000));
        assertTrue(firstStream.isSuccess());

        // assert that the second promise completes successfully
        assertTrue(secondStream.await(1_000));
        assertTrue(secondStream.isSuccess());

        // assert parent attributes
        assertEquals(connectionPool, mockParentChannel.attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).get());
        assertNotNull(mockParentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get());

        // assert parent was not closed
        assertFalse(mockParentChannel.wasExplicitlyClosed());

        // assert stream pipeline
        var mockParentPipeline = mockParentChannel.mockPipeline();
        assertTrue(mockParentPipeline.containsInOrder(ReleaseOnExceptionHandler.class));

        // assert stream attributes
        var idx = 0;
        for (var mockStreamPromise : Arrays.asList(firstStream, secondStream)) {
            var mockStreamResult = mockStreamPromise.get();
            assertInstanceOf(MockChannel.Stream.class, mockStreamResult);
            var mockStream = (MockChannel.Stream) mockStreamResult;
            assertEquals(connectionPool, mockStream.attr(CHANNEL_POOL).get(), "Missing attribute for stream " + idx);

            // assert stream pipeline
            var mockStreamPipeline = mockStream.mockPipeline();
            assertTrue(mockStreamPipeline.containsInOrder(
                    Http2ToHttpInboundAdapter.class,
                    HttpToHttp2OutboundAdapter.class,
                    Http2StreamExceptionHandler.class),
                    "The stream " + idx + "'s pipeline does not have the expected handlers");
            idx++;
        }
        // assert the multiplexed channel was used for the enw stream
        assertEquals(1, connectionPool.parentCount(), "A single multiplexed channel is in the pool");
    }

    @Test
    public void succeedsReleasingStream() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();
        connectionPool.acquire(resultPromise);
        resultPromise.await(1_000);

        // Make sure that the result is successful
        assertTrue(resultPromise.isSuccess());

        // -- Act
        connectionPool.release(resultPromise.get());

        // -- Assert

        // assert that the stream was closed.
        assertTrue(mockStream.wasExplicitlyClosed());
        // assert that the multiplexed channel is still present in the cache
        assertEquals(1, connectionPool.parentCount(), "The multiplexed channel is still present in the pool");
    }

    @Test
    public void closesAndRemovesAMultiplexedChannelWhenCanBe() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();
        connectionPool.acquire(resultPromise);
        resultPromise.await(1_000);

        // Make sure that the result is successful
        assertTrue(resultPromise.isSuccess());

        // Set the multiplexed channel as ready to be released
        var multiplexedChannel = mockParentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get();

        // validate that the multiplexed channel is still open.
        assertFalse(multiplexedChannel.isClosed());

        // -- Act
        multiplexedChannel.closeToNewStreams();
        connectionPool.release(resultPromise.get());

        // -- Assert

        // assert that the stream was closed.
        assertTrue(mockStream.wasExplicitlyClosed());

        // assert that the multiplexed channel was removed
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was removed from the pool");

        // assert that the multiplexed channel is closed.
        assertTrue(multiplexedChannel.isClosed());
    }

    @Test
    public void closesButDoesNotReleaseANonStreamChannel() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();
        connectionPool.acquire(resultPromise);
        resultPromise.await(1_000);

        // Make sure that the result is successful
        assertTrue(resultPromise.isSuccess());

        // -- Act & assert

        // A non-stream channel cannot be released to this pool
        assertThrows(AssertionError.class, () -> connectionPool.release(mockParentChannel));

        // assert that the channel was closed.
        assertTrue(mockParentChannel.wasExplicitlyClosed());
    }

    @Test
    public void closesButDoesNotReleaseStreamWithoutMultiplexedChannel() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();
        connectionPool.acquire(resultPromise);
        resultPromise.await(1_000);

        // Make sure that the result is successful
        assertTrue(resultPromise.isSuccess());

        // -- Act & assert

        // A stream channel without a multiplexed channel attached cannot be released to this pool
        mockParentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).set(null);
        assertThrows(AssertionError.class, () -> connectionPool.release(mockStream));

        // assert that the channel was closed.
        assertTrue(mockStream.wasExplicitlyClosed());
    }

    @Test
    public void succeedsWhenWindowSizeIncrementThrows() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool with a local flow controller that throws
        // when setting the initial window size.
        var mockParentChannel = setupMockChannel(HttpVersion.HTTP_2, null, true);
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes successfully
        assertTrue(resultPromise.await(1_000));
        assertTrue(resultPromise.isSuccess());
        assertEquals(mockStream, resultPromise.get());

        // assert initial window size was NOT set to the expected value
        var http2Connection = (MockHttp2Connection) mockParentChannel.attr(NettyHttp2Constants.HTTP2_CONNECTION).get();
        assertNotNull(http2Connection);
        assertNull(http2Connection.initialWindowSizeSet());

        // assert parent attributes
        assertEquals(connectionPool, mockParentChannel.attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).get());
        assertNotNull(mockParentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get());

        // assert parent was not closed
        assertFalse(mockParentChannel.wasExplicitlyClosed());

        // assert stream pipeline
        var mockParentPipeline = mockParentChannel.mockPipeline();
        assertTrue(mockParentPipeline.containsInOrder(ReleaseOnExceptionHandler.class));

        // assert stream attributes
        assertEquals(connectionPool, mockStream.attr(CHANNEL_POOL).get());

        // assert stream pipeline
        var mockStreamPipeline = mockStream.mockPipeline();
        assertTrue(mockStreamPipeline.containsInOrder(
                Http2ToHttpInboundAdapter.class,
                HttpToHttp2OutboundAdapter.class,
                Http2StreamExceptionHandler.class));

        // assert a multiplexed channel was added to the pool
        assertEquals(1, connectionPool.parentCount(), "A multiplexed channel was added to the pool");
    }

    @Test
    public void failsWithNonH2VersionFuture() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool parent channel with a version future
        // completes to a non-supported HTTP version
        var mockParentChannel = setupMockChannel(HttpVersion.HTTP_1_1, null, false);
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes with a failure
        assertTrue(resultPromise.await(1_000));
        assertFalse(resultPromise.isSuccess());
        var throwable = resultPromise.cause();
        assertNotNull(throwable);
        assertTrue(throwable.getMessage().contains("HTTP/1.1"));
        assertTrue(mockParentChannel.wasExplicitlyClosed(), "The parent channel was explicitly closed");
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was NOT added to the pool");
    }

    @Test
    public void failsWhenTheVersionFutureFails() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool with a parent channel with a version future completed to a failed state.
        var mockParentChannel = setupMockChannel(null, new TransportException("boom"), false);
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes with a failure
        assertTrue(resultPromise.await(1_000));
        assertFalse(resultPromise.isSuccess());
        var throwable = resultPromise.cause();
        assertNotNull(throwable);
        assertEquals("boom", throwable.getMessage());
        assertTrue(mockParentChannel.wasExplicitlyClosed(), "The parent channel was explicitly closed");
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was NOT added to the pool");
    }

    @Test
    public void failsWhenTheStreamFails() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap to fail.
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newFailedFuture(new IOException("boom"));

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes with a failure
        assertTrue(resultPromise.await(1_000));
        assertFalse(resultPromise.isSuccess());
        var throwable = resultPromise.cause();
        assertNotNull(throwable);
        assertEquals("boom", throwable.getMessage());
        assertInstanceOf(IOException.class, throwable);
        assertTrue(mockParentChannel.wasExplicitlyClosed(), "The parent channel was explicitly closed");
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was NOT added to the pool");
    }

    @Test
    public void ailsWhenTheStreamIsClosedBeforeFinishingSettingUp() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var mockParentChannel = setupValidMockChannel();
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireSucceed(mockParentChannel)
                .build();

        // Set up the stream bootstrap
        var mockStream = MockChannel.builder()
                .parent(mockParentChannel)
                .buildStream();
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newSucceededFuture(mockStream);

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                (parentChannel, maxConcurrentStreams, streamBootstrap) -> {
                    var multiplexedChannel =
                            new MultiplexedChannel(parentChannel, maxConcurrentStreams, streamBootstrap);
                    multiplexedChannel.closeToNewStreams();
                    return multiplexedChannel;
                });

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes successfully
        assertTrue(resultPromise.await(1_000));
        assertFalse(resultPromise.isSuccess());
        var throwable = resultPromise.cause();
        assertNotNull(throwable);
        assertEquals("Connection was closed while creating a new stream.", throwable.getMessage());
        assertInstanceOf(TransportException.class, throwable);
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was NOT added to the pool");
    }

    @Test
    public void failsWhenTheParentFails() throws Exception {
        // --- Arrange

        // Set up the mock base channel pool
        var basePool = MockChannelPool.builder()
                .eventLoopGroup(eventLoopGroup)
                .onAcquireFail(new IOException("parent failed"))
                .build();

        // Set up the stream bootstrap
        var streamFuture = eventLoopGroup.next().<Http2StreamChannel>newFailedFuture(new IOException("stream failed"));

        // Set up the connection pool
        var connectionPool = new Http2MultiplexedConnectionPool(basePool,
                eventLoopGroup,
                ch -> streamFuture,
                MultiplexedChannel::new);

        // Set up result promise
        var resultPromise = eventLoopGroup.next().<Channel>newPromise();

        // --- Act
        connectionPool.acquire(resultPromise);

        // --- Assert

        // assert that the promise completes with a failure
        assertTrue(resultPromise.await(1_000));
        assertFalse(resultPromise.isSuccess());
        var throwable = resultPromise.cause();
        assertNotNull(throwable);
        assertEquals("parent failed", throwable.getMessage());
        assertInstanceOf(IOException.class, throwable);
        assertEquals(0, connectionPool.parentCount(), "A multiplexed channel was NOT added to the pool");
    }

    private static MockChannel setupValidMockChannel() {
        return setupMockChannel(HttpVersion.HTTP_2, null, false);
    }

    private static MockChannel setupMockChannel(
            HttpVersion version,
            Throwable cause,
            boolean incrementWindowSizeThrows
    ) {
        CompletableFuture<HttpVersion> versionFuture;
        if (version != null && cause != null) {
            throw new IllegalArgumentException("cause and version cannot be both not null");
        }
        if (version == null && cause == null) {
            throw new IllegalArgumentException("cause and version cannot be null");
        }
        if (version != null) {
            versionFuture = CompletableFuture.completedFuture(version);
        } else {
            versionFuture = CompletableFuture.failedFuture(cause);
        }
        var mockParentChannel = MockChannel.builder().build();
        var http2Connection = new MockHttp2Connection(incrementWindowSizeThrows);
        mockParentChannel.attr(NettyHttp2Constants.HTTP2_INITIAL_WINDOW_SIZE).set(EXPECTED_INITIAL_WINDOW_SIZE);
        mockParentChannel.attr(NettyConstants.HTTP_VERSION_FUTURE).set(versionFuture);
        mockParentChannel.attr(NettyHttp2Constants.HTTP2_MAX_CONCURRENT_STREAMS).set(11L);
        mockParentChannel.attr(NettyHttp2Constants.HTTP2_CONNECTION).set(http2Connection);
        return mockParentChannel;
    }
}
