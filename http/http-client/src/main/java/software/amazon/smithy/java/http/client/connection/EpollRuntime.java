/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.channel.epoll.Epoll;
import java.io.IOException;
import java.io.UncheckedIOException;
import software.amazon.smithy.java.logging.InternalLogger;

final class EpollRuntime implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EpollRuntime.class);

    /** EPOLLHUP is a fixed Linux constant not exported by Netty's Native; peer hangup. */
    static final int EPOLLHUP = EpollReactor.EPOLLHUP;

    private static final int MAX_FDS = 1 << 16;
    private static final int EVENT_ARRAY_LEN = 256;

    private final EpollReactor[] reactors;

    private EpollRuntime(int shards, int maxFds, int eventArrayLen) throws IOException {
        if (shards < 1) {
            throw new IllegalArgumentException("shards must be >= 1");
        }
        this.reactors = new EpollReactor[shards];
        for (int i = 0; i < shards; i++) {
            reactors[i] = new EpollReactor("smithy-epoll-rt-" + i, maxFds, eventArrayLen);
        }
    }

    /**
     * @return true if the native epoll transport is usable on this host (Linux with the native
     *     library loadable). When false, callers MUST use the JDK NIO socket path instead.
     */
    static boolean isAvailable() {
        return Epoll.isAvailable();
    }

    /**
     * @return the cause of unavailability for diagnostics, or null if epoll is available.
     */
    static Throwable unavailabilityCause() {
        return Epoll.unavailabilityCause();
    }

    /**
     * The lazily-started, process-global epoll runtime. Only call after confirming
     * {@link #isAvailable()}.
     */
    static EpollRuntime shared() {
        return Holder.INSTANCE;
    }

    // Initialization-on-demand holder: the runtime (and its native epoll fds + poller threads) is
    // created only on first use, so merely loading this class on a non-Linux host costs nothing.
    private static final class Holder {
        static final EpollRuntime INSTANCE = create();

        private static EpollRuntime create() {
            int shards = shardCount();
            try {
                EpollRuntime runtime = new EpollRuntime(shards, MAX_FDS, EVENT_ARRAY_LEN);
                runtime.start();
                LOGGER.info("Started epoll transport runtime with {} poller thread(s)", shards);
                return runtime;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to start epoll transport runtime", e);
            }
        }
    }

    /**
     * Size the runtime by the same {@code jdk.poller*} system properties that drive the JDK
     * virtual-thread blocking model, so both models are configured by one knob. The JDK runs
     * {@code jdk.readPollers} read-poller threads plus {@code jdk.writePollers} write-poller threads
     * (each count must be a power of two). Our reactor handles both readiness directions in a single
     * epfd per shard, so we use {@code readPollers + writePollers} shards — the same total number of
     * epoll poller platform threads the JDK would start.
     */
    private static int shardCount() {
        int read = pollerProp("jdk.readPollers", 1);
        int write = pollerProp("jdk.writePollers", 1);
        return read + write;
    }

    private static int pollerProp(String name, int defaultValue) {
        String s = System.getProperty(name);
        if (s == null) {
            return defaultValue;
        }
        int v = Integer.parseInt(s.trim());
        // Match the JDK's own constraint so the same flag is valid for both models.
        if (v != Integer.highestOneBit(v)) {
            throw new IllegalArgumentException(name + " is set to a value that is not a power of 2: " + v);
        }
        return v;
    }

    private void start() {
        for (EpollReactor r : reactors) {
            r.start();
        }
    }

    int shards() {
        return reactors.length;
    }

    private EpollReactor reactorFor(int fd) {
        return reactors[(fd & 0x7fffffff) % reactors.length];
    }

    void register(int fd, EpollChannel ch, int flags) throws IOException {
        reactorFor(fd).register(fd, ch, flags);
    }

    void ctlMod(int fd, int flags) throws IOException {
        reactorFor(fd).ctlMod(fd, flags);
    }

    void deregister(int fd) {
        reactorFor(fd).deregister(fd);
    }

    @Override
    public void close() {
        for (EpollReactor r : reactors) {
            r.close();
        }
    }
}
