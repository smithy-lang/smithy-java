/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package io.netty.channel.epoll;

import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Thin bridge exposing Netty's package-private epoll bits to the connection package. */
public final class EpollAccess {

    private EpollAccess() {}

    // --- epoll event flags (already public on Native, re-exported for convenience) ---
    public static final int EPOLLIN = Native.EPOLLIN;
    public static final int EPOLLOUT = Native.EPOLLOUT;
    public static final int EPOLLET = Native.EPOLLET;
    public static final int EPOLLRDHUP = Native.EPOLLRDHUP;
    public static final int EPOLLERR = Native.EPOLLERR;

    // --- epfd / eventfd lifecycle (public Native factories) ---
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

    // --- epoll_ctl (public on Native; re-exported so callers need only this class) ---
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
     * Blocking {@code epoll_wait} into {@code events}. {@code timeoutMillis < 0} waits indefinitely;
     * {@code 0} polls. Returns the number of ready descriptors. This is the package-private
     * {@link Native#epollWait(FileDescriptor, EpollEventArray, int)} overload.
     */
    public static int epollWait(FileDescriptor epfd, EpollEventArray events, int timeoutMillis) throws IOException {
        return Native.epollWait(epfd, events, timeoutMillis);
    }

    // --- EpollEventArray (package-private ctor + readers) ---
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

    /** Native memory address of a direct {@link ByteBuffer} (for the raw-address recv/send path). */
    public static long memoryAddress(ByteBuffer directBuffer) {
        return Buffer.memoryAddress(directBuffer);
    }
}
