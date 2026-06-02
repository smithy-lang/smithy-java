/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.channel.epoll.EpollAccess;
import io.netty.channel.epoll.EpollEventArray;
import io.netty.channel.unix.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.java.logging.InternalLogger;

final class EpollReactor implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EpollReactor.class);

    /** EPOLLHUP is a fixed Linux constant not exported by Netty's Native; peer hangup. */
    static final int EPOLLHUP = 0x010;

    private final FileDescriptor epollFd;
    private final FileDescriptor eventFd;
    private final int epfd;
    private final int evfd;
    private final EpollEventArray events;
    private final AtomicReferenceArray<EpollChannel> channels;
    private final Thread poller;
    private volatile boolean running = true;

    EpollReactor(String name, int maxFds, int eventArrayLen) throws IOException {
        this.epollFd = EpollAccess.newEpollCreate();
        this.eventFd = EpollAccess.newEventFd();
        this.epfd = epollFd.intValue();
        this.evfd = eventFd.intValue();
        this.events = EpollAccess.newEventArray(eventArrayLen);
        this.channels = new AtomicReferenceArray<>(maxFds);
        // Register the wakeup eventfd (level-triggered EPOLLIN) so shutdown can unblock the poller.
        EpollAccess.epollCtlAdd(epfd, evfd, EpollAccess.EPOLLIN);
        this.poller = new Thread(this::pollLoop, name + "-poller");
        this.poller.setDaemon(true);
    }

    void start() {
        poller.start();
    }

    int epfd() {
        return epfd;
    }

    /**
     * Register a freshly-created fd's channel. The slot is published BEFORE {@code epoll_ctl ADD} so
     * the poller can never see an event for an fd it cannot map.
     */
    void register(int fd, EpollChannel ch, int flags) throws IOException {
        if (fd >= channels.length()) {
            throw new IOException("fd " + fd + " exceeds map capacity " + channels.length());
        }
        channels.set(fd, ch);
        EpollAccess.epollCtlAdd(epfd, fd, flags);
    }

    /** Change interest flags for an already-registered fd (e.g. arm/disarm EPOLLOUT). */
    void ctlMod(int fd, int flags) throws IOException {
        EpollAccess.epollCtlMod(epfd, fd, flags);
    }

    /** Remove an fd from epoll and clear its slot. Safe to call once per fd before close(). */
    void deregister(int fd) {
        try {
            EpollAccess.epollCtlDel(epfd, fd);
        } catch (IOException ignore) {
            // fd may already be gone (e.g. closed); DEL on a closed fd is harmless here.
        }
        if (fd < channels.length()) {
            channels.set(fd, null);
        }
    }

    private void pollLoop() {
        try {
            while (running) {
                int n = EpollAccess.epollWait(epollFd, events, -1); // block until ready or wakeup
                for (int i = 0; i < n; i++) {
                    int fd = EpollAccess.fd(events, i);
                    int ev = EpollAccess.events(events, i);
                    if (fd == evfd) {
                        EpollAccess.eventFdRead(evfd); // drain the wakeup
                        continue;
                    }
                    EpollChannel ch = channels.get(fd);
                    if (ch == null) {
                        continue;
                    }
                    try {
                        ch.onReady(ev);
                    } catch (Throwable t) {
                        LOGGER.error("epoll onReady failed for fd " + fd, t);
                    }
                }
            }
        } catch (Throwable t) {
            if (running) {
                LOGGER.error("epoll poller died", t);
            }
        }
    }

    /** Wake the poller (used to unblock epoll_wait during shutdown). */
    void wakeup() {
        EpollAccess.eventFdWrite(evfd, 1L);
    }

    @Override
    public void close() {
        running = false;
        wakeup();
        try {
            poller.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            epollFd.close();
        } catch (IOException ignore) {
            // best effort
        }
        try {
            eventFd.close();
        } catch (IOException ignore) {
            // best effort
        }
        EpollAccess.free(events);
    }
}
