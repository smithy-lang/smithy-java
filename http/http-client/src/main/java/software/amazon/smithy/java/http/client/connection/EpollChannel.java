/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.channel.epoll.EpollAccess;
import io.netty.channel.unix.Socket;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A single TCP connection with a <b>blocking virtual-thread API</b> ({@link #readAddress}/
 * {@link #writeAddress}) backed by <b>persistent</b> epoll registration in an {@link EpollRuntime}.
 *
 * <p>This class is package-private and instantiated only when the experimental epoll transport
 * backend is enabled and {@link EpollRuntime#isAvailable()} is true.
 */
final class EpollChannel {

    // Sentinel returned by the guarded syscall helpers when the channel was closed before the syscall
    // could run under the fd lock. Distinct from any recv/send/writev result (which are >= -1), so the
    // I/O loops route it back to their top-of-loop closed handling instead of treating it as data/EOF.
    private static final int CLOSED = -2;

    private final EpollRuntime runtime;
    private final Socket socket;
    private final int fd;
    private final int baseFlags; // EPOLLIN | EPOLLET | EPOLLRDHUP
    // Shared wheel-timer watchdog for read deadlines (the SAME one the NIO SSLEngineTransport path
    // uses). On the hot read path we park UNTIMED and let a one-shot wheel timeout close the channel
    // if the deadline passes, an O(1) bucket arm/cancel per read. This deliberately avoids
    // LockSupport.parkNanos, which arms a JDK DelayScheduler timer entry per read (a measurable
    // cross-thread signal/unpark tax that the NIO path does not pay). Null => untimed reads.
    private final Timer readTimer;

    // Read-direction park state.
    private volatile Thread reader;
    private volatile boolean readReady;
    // Set by the read watchdog immediately before it closes the channel, so a parked reader that wakes
    // to a closed channel can distinguish a deadline expiry (SocketTimeoutException) from a normal EOF.
    private volatile boolean readTimedOut;
    // Write-direction park state.
    private volatile Thread writer;
    private volatile boolean writeReady;

    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean epollOutArmed;

    // Guards the file descriptor against the close/syscall race. Every fd syscall (recv/send/writev)
    // runs under the read lock; close() takes the write lock before calling socket.close(). This
    // establishes the invariant that socket.close() happens-after the last in-flight syscall and that no
    // new syscall starts once the fd has been released to the kernel. Without it, a syscall that has
    // already passed the `closed` check could run recv(2)/send(2) on the raw fd number after close()
    // released it and the kernel handed that number to another connection, reading or writing the wrong
    // socket (cross-connection data disclosure on a TLS transport). Reads and writes hold the lock only
    // around the non-blocking syscall itself (never across a park), so a reader and writer still proceed
    // concurrently and close() never waits on a parked thread.
    private final ReentrantReadWriteLock fdLock = new ReentrantReadWriteLock();

    private EpollChannel(EpollRuntime runtime, Socket socket, Timer readTimer) {
        this.runtime = runtime;
        this.socket = socket;
        this.readTimer = readTimer;
        this.fd = socket.intValue();
        this.baseFlags = EpollAccess.EPOLLIN | EpollAccess.EPOLLET | EpollAccess.EPOLLRDHUP;
    }

    // ---------------------------------------------------------------------
    // Factory
    // ---------------------------------------------------------------------

    /**
     * Open a fresh non-blocking stream socket, apply socket options, register it persistently, and
     * connect (parking until writable if the kernel returns EINPROGRESS).
     *
     * @param runtime the shared epoll runtime
     * @param remote the resolved remote address to connect to
     * @param connectTimeoutMs connect deadline in milliseconds; {@code 0} means wait indefinitely
     * @param options socket options to apply before connecting
     */
    static EpollChannel connect(
            EpollRuntime runtime,
            InetSocketAddress remote,
            int connectTimeoutMs,
            SocketOptions options,
            Timer readTimer
    ) throws IOException {
        Socket socket = Socket.newSocketStream();
        boolean ok = false;
        try {
            options.applyTo(socket);
            EpollChannel ch = new EpollChannel(runtime, socket, readTimer);
            runtime.register(ch.fd, ch, ch.baseFlags); // register before connect so events map
            ch.doConnect(remote, deadlineNanos(connectTimeoutMs));
            ok = true;
            return ch;
        } finally {
            if (!ok) {
                runtime.deregister(socket.intValue());
                try {
                    socket.close();
                } catch (IOException ignore) {
                    // best effort
                }
            }
        }
    }

    private void doConnect(SocketAddress remote, long deadline) throws IOException {
        if (socket.connect(remote)) {
            return; // connected immediately (common on loopback)
        }
        // EINPROGRESS: arm EPOLLOUT, park until writable, then finish.
        armEpollOut();
        try {
            if (!awaitWritable(deadline)) {
                throw new SocketTimeoutException("Connect timed out");
            }
            while (!socket.finishConnect()) {
                // Spurious wakeup before completion, so loop until finished or error thrown.
                if (!awaitWritable(deadline)) {
                    throw new SocketTimeoutException("Connect timed out");
                }
            }
        } finally {
            disarmEpollOut();
        }
    }

    // ---------------------------------------------------------------------
    // Blocking VT-style raw-address I/O (the hot path)
    // ---------------------------------------------------------------------

    /**
     * Read into {@code [base+pos, base+limit)} of an off-heap region (the memory address of a direct
     * buffer, obtained via {@code io.netty.channel.unix.Buffer#memoryAddress}). Blocks the calling virtual
     * thread until at least one byte is read (returning the count), EOF/peer-close/local-close is observed
     * (returning {@code -1}), or, if {@code timeoutMs > 0}, the deadline passes (throwing
     * {@link SocketTimeoutException}).
     *
     * <p>Uses Netty's {@code recvAddress}, which goes straight to {@code recv(2)} on the raw pointer,
     * skipping the {@code GetDirectBufferAddress} + {@code ByteBuffer} bounds/{@code instanceof}
     * overhead the {@code ByteBuffer} overload pays per call.
     *
     * @param base direct-buffer base memory address
     * @param pos start offset within the region
     * @param limit end offset within the region
     * @param timeoutMs read deadline in milliseconds; {@code 0} means wait indefinitely
     * @return bytes read ({@code >0}), or {@code -1} on EOF/close
     */
    int readAddress(long base, int pos, int limit, int timeoutMs) throws IOException {
        Timeout watchdog = (timeoutMs > 0 && readTimer != null)
                ? readTimer.newTimeout(t -> fireReadTimeout(), timeoutMs, TimeUnit.MILLISECONDS)
                : null;
        try {
            for (;;) {
                if (closed.get()) {
                    if (readTimedOut) {
                        throw new SocketTimeoutException("Read timed out after " + timeoutMs + "ms");
                    }
                    return -1;
                }
                if (pos >= limit) {
                    return 0;
                }
                int n = guardedRecv(base, pos, limit); // >0 bytes, 0 EAGAIN, -1 EOF, CLOSED if closed
                if (n == CLOSED) {
                    continue; // re-run the top-of-loop closed handling
                }
                if (n > 0) {
                    return n;
                }
                if (n < 0) {
                    return -1; // EOF
                }
                awaitReadable(watchdog == null ? deadlineNanos(timeoutMs) : 0L);
            }
        } finally {
            if (watchdog != null) {
                watchdog.cancel();
            }
        }
    }

    private void fireReadTimeout() {
        readTimedOut = true;
        close();
    }

    /**
     * Write all of {@code [base+pos, base+limit)} from an off-heap region (untimed, matching the JDK
     * blocking-channel write path). Parks on EPOLLOUT under back-pressure, arming it exactly once for
     * the duration of this call.
     *
     * @param base direct-buffer base memory address
     * @param pos start offset within the region
     * @param limit end offset within the region
     */
    void writeAddress(long base, int pos, int limit) throws IOException {
        boolean armed = false;
        try {
            while (pos < limit) {
                if (closed.get()) {
                    throw new IOException("channel closed");
                }
                int n = guardedSend(base, pos, limit); // >0 bytes, 0 EAGAIN, CLOSED if closed
                if (n == CLOSED) {
                    continue; // re-run the top-of-loop closed handling (throws)
                }
                if (n > 0) {
                    pos += n;
                    continue;
                }
                if (!armed) {
                    armEpollOut();
                    armed = true;
                }
                awaitWritable(0L); // untimed
            }
        } finally {
            if (armed) {
                disarmEpollOut();
            }
        }
    }

    /**
     * Write all remaining bytes in the provided buffers using {@code writev(2)}. Parks on EPOLLOUT
     * under back-pressure, matching the blocking semantics of {@link #writeAddress}.
     *
     * @return bytes written, equal to the original total remaining byte count unless an exception is
     *     thrown
     */
    long writev(ByteBuffer[] buffers, int offset, int length) throws IOException {
        int end = offset + length;
        long remaining = remaining(buffers, offset, end);
        long written = 0;
        boolean armed = false;
        try {
            while (remaining > 0) {
                if (closed.get()) {
                    throw new IOException("channel closed");
                }
                int first = firstRemaining(buffers, offset, end);
                long n = guardedWritev(buffers, first, end - first, remaining); // >0, 0 EAGAIN, CLOSED
                if (n == CLOSED) {
                    continue; // re-run the top-of-loop closed handling (throws)
                }
                if (n > 0) {
                    advance(buffers, first, end, n);
                    written += n;
                    remaining -= n;
                    continue;
                }
                if (!armed) {
                    armEpollOut();
                    armed = true;
                }
                awaitWritable(0L); // untimed
            }
            return written;
        } finally {
            if (armed) {
                disarmEpollOut();
            }
        }
    }

    private static int firstRemaining(ByteBuffer[] buffers, int offset, int end) {
        for (int i = offset; i < end; i++) {
            if (buffers[i].hasRemaining()) {
                return i;
            }
        }
        return end;
    }

    private static void advance(ByteBuffer[] buffers, int offset, int end, long bytes) {
        for (int i = offset; i < end && bytes > 0; i++) {
            ByteBuffer buffer = buffers[i];
            int remaining = buffer.remaining();
            if (remaining == 0) {
                continue;
            }
            int consumed = (int) Math.min(bytes, remaining);
            buffer.position(buffer.position() + consumed);
            bytes -= consumed;
        }
    }

    private static long remaining(ByteBuffer[] buffers, int offset, int end) {
        long result = 0;
        for (int i = offset; i < end; i++) {
            result += buffers[i].remaining();
        }
        return result;
    }

    // The fd syscalls below run under the read lock with a re-check of `closed` inside the lock. close()
    // holds the write lock while it releases the fd, so once it wins the lock these helpers see `closed`
    // and return CLOSED without touching the fd; conversely, a syscall already holding the read lock
    // completes before close() can release the fd. Either way recv/send/writev never runs on a released
    // (and possibly reused) fd. The lock is dropped immediately after the syscall so parking happens
    // unlocked.

    private int guardedRecv(long base, int pos, int limit) throws IOException {
        fdLock.readLock().lock();
        try {
            if (closed.get()) {
                return CLOSED;
            }
            return socket.recvAddress(base, pos, limit);
        } finally {
            fdLock.readLock().unlock();
        }
    }

    private int guardedSend(long base, int pos, int limit) throws IOException {
        fdLock.readLock().lock();
        try {
            if (closed.get()) {
                return CLOSED;
            }
            return socket.sendAddress(base, pos, limit);
        } finally {
            fdLock.readLock().unlock();
        }
    }

    private long guardedWritev(ByteBuffer[] buffers, int offset, int length, long maxBytes) throws IOException {
        fdLock.readLock().lock();
        try {
            if (closed.get()) {
                return CLOSED;
            }
            return socket.writev(buffers, offset, length, maxBytes);
        } finally {
            fdLock.readLock().unlock();
        }
    }

    private boolean awaitReadable(long deadline) throws InterruptedIOException {
        reader = Thread.currentThread(); // publish waiter (volatile store)
        try {
            while (!readReady && !closed.get()) {
                checkInterrupted();
                if (deadline == 0L) {
                    LockSupport.park(this); // re-check after publishing => no lost wakeup
                } else {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        return false; // timed out
                    }
                    LockSupport.parkNanos(this, remaining);
                    if (!readReady && !closed.get() && System.nanoTime() >= deadline) {
                        return false;
                    }
                }
            }
        } finally {
            reader = null;
        }
        readReady = false; // consume the readiness edge
        return true;
    }

    /**
     * Park until writable. Returns true on a writability edge (or close), false if the deadline
     * passed.
     *
     * @param deadline {@link System#nanoTime()}-relative deadline, or {@code 0} for no deadline
     */
    private boolean awaitWritable(long deadline) throws InterruptedIOException {
        writer = Thread.currentThread();
        try {
            while (!writeReady && !closed.get()) {
                checkInterrupted();
                if (deadline == 0L) {
                    LockSupport.park(this);
                } else {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        return false;
                    }
                    LockSupport.parkNanos(this, remaining);
                    if (!writeReady && !closed.get() && System.nanoTime() >= deadline) {
                        return false;
                    }
                }
            }
        } finally {
            writer = null;
        }
        writeReady = false;
        return true;
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for socket readiness");
        }
    }

    // ---------------------------------------------------------------------
    // Poller callback (runs on the EpollReactor poller thread)
    // ---------------------------------------------------------------------

    /** Called by the reactor's poller when this fd has events. Sets ready flags then unparks. */
    void onReady(int ev) {
        boolean errOrHup = (ev & (EpollAccess.EPOLLERR | EpollRuntime.EPOLLHUP)) != 0;
        if (errOrHup) {
            // Surface to both directions; the parked recv/send will return EOF/throw.
            wakeRead();
            wakeWrite();
            return;
        }
        if ((ev & (EpollAccess.EPOLLIN | EpollAccess.EPOLLRDHUP)) != 0) {
            wakeRead();
        }
        if ((ev & EpollAccess.EPOLLOUT) != 0) {
            wakeWrite();
        }
    }

    private void wakeRead() {
        readReady = true; // set flag (volatile store) BEFORE reading waiter
        Thread t = reader; // (volatile load)
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private void wakeWrite() {
        writeReady = true;
        Thread t = writer;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    // ---------------------------------------------------------------------
    // EPOLLOUT arm/disarm: the only epoll_ctl on the hot path, and only under write back-pressure
    // ---------------------------------------------------------------------

    private void armEpollOut() throws IOException {
        if (!epollOutArmed) {
            runtime.ctlMod(fd, baseFlags | EpollAccess.EPOLLOUT);
            epollOutArmed = true;
        }
    }

    private void disarmEpollOut() {
        if (epollOutArmed) {
            try {
                runtime.ctlMod(fd, baseFlags);
            } catch (IOException ignore) {
                // channel may be closing; ignore.
            }
            epollOutArmed = false;
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle / accessors
    // ---------------------------------------------------------------------

    boolean isOpen() {
        return !closed.get();
    }

    int fd() {
        return fd;
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // `closed` is now set, so any syscall helper that has not yet taken the read lock will see it and
        // bail. Wake parked waiters first (this never blocks): a thread parked in awaitReadable/Writable
        // holds no lock, so it can wake, observe `closed`, and release without contending for the fd lock.
        readReady = true;
        writeReady = true;
        Thread r = reader;
        Thread w = writer;
        if (r != null) {
            LockSupport.unpark(r);
        }
        if (w != null) {
            LockSupport.unpark(w);
        }
        // Take the write lock to release the fd. This drains any syscall already in flight under the read
        // lock and blocks new ones, so socket.close() (which returns the fd number to the kernel) cannot
        // overlap a recv/send/writev. The non-blocking syscalls return promptly, so this wait is bounded.
        fdLock.writeLock().lock();
        try {
            runtime.deregister(fd);
            socket.close();
        } catch (IOException ignore) {
            // best effort
        } finally {
            fdLock.writeLock().unlock();
        }
    }

    private static long deadlineNanos(int timeoutMs) {
        return timeoutMs > 0 ? System.nanoTime() + (long) timeoutMs * 1_000_000L : 0L;
    }

    /**
     * Socket options applied to the native epoll socket before connecting. Because the epoll backend
     * does not go through {@link HttpSocketFactory}, these mirror the options the JDK NIO path would
     * otherwise receive so the two backends are compared on equal footing. {@code null} buffer sizes
     * leave the kernel default (autotuned).
     */
    record SocketOptions(Integer receiveBufferSize, Integer sendBufferSize, boolean keepAlive) {
        void applyTo(Socket socket) throws IOException {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(keepAlive);
            if (receiveBufferSize != null) {
                socket.setReceiveBufferSize(receiveBufferSize);
            }
            if (sendBufferSize != null) {
                socket.setSendBufferSize(sendBufferSize);
            }
        }
    }
}
