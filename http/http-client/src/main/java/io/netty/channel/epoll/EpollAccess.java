/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.netty.channel.epoll;

import io.netty.channel.unix.FileDescriptor;
import java.io.IOException;

/**
 * Bridge to Netty's epoll internals. Lives in this package only because {@link EpollEventArray} and the
 * {@code epollWait(fd, array, int)} overload are package-private; the rest is public {@link Native} API
 * re-exported so callers depend on one class. Classpath-only (split package is illegal under JPMS) and
 * built on unsupported API, so a Netty upgrade can break it. HTTP client implementation detail.
 */
public final class EpollAccess {

    private EpollAccess() {}

    // --- epoll event flags ---
    public static final int EPOLLIN = Native.EPOLLIN;
    public static final int EPOLLOUT = Native.EPOLLOUT;
    public static final int EPOLLET = Native.EPOLLET;
    public static final int EPOLLRDHUP = Native.EPOLLRDHUP;
    public static final int EPOLLERR = Native.EPOLLERR;

    // --- epfd / eventfd lifecycle ---
    public static FileDescriptor newEpollCreate() {
        return Native.newEpollCreate();
    }

    public static FileDescriptor newEventFd() {
        return Native.newEventFd();
    }

    public static void eventFdWrite(int fd, long value) {
        Native.eventFdWrite(fd, value);
    }

    public static void eventFdRead(int fd) {
        Native.eventFdRead(fd);
    }

    // --- epoll_ctl ---
    public static void epollCtlAdd(int efd, int fd, int flags) throws IOException {
        Native.epollCtlAdd(efd, fd, flags);
    }

    public static void epollCtlMod(int efd, int fd, int flags) throws IOException {
        Native.epollCtlMod(efd, fd, flags);
    }

    public static void epollCtlDel(int efd, int fd) throws IOException {
        Native.epollCtlDel(efd, fd);
    }

    /**
     * Blocking {@code epoll_wait} into {@code events}. {@code timeoutMillis < 0} waits indefinitely,
     * {@code 0} polls. Returns the number of ready descriptors.
     */
    public static int epollWait(FileDescriptor epfd, EpollEventArray events, int timeoutMillis) throws IOException {
        return Native.epollWait(epfd, events, timeoutMillis);
    }

    // --- EpollEventArray (package-private) ---
    public static EpollEventArray newEventArray(int length) {
        return new EpollEventArray(length);
    }

    public static int events(EpollEventArray arr, int index) {
        return arr.events(index);
    }

    public static int fd(EpollEventArray arr, int index) {
        return arr.fd(index);
    }

    public static int length(EpollEventArray arr) {
        return arr.length();
    }

    public static void increase(EpollEventArray arr) {
        arr.increase();
    }

    public static void free(EpollEventArray arr) {
        arr.free();
    }
}
