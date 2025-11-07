/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Smithy Java client Netty HTTP transport.
 */
module software.amazon.smithy.java.client.http.netty {
    requires software.amazon.smithy.java.http.api;
    requires software.amazon.smithy.java.client.core;
    requires software.amazon.smithy.java.client.http;
    requires software.amazon.smithy.java.io;
    requires software.amazon.smithy.java.logging;
    requires software.amazon.smithy.java.context;
    requires software.amazon.smithy.java.core;

    requires io.netty.handler;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires io.netty.transport;
    requires io.netty.buffer;
    requires io.netty.common;
    requires java.net.http;
    requires jdk.httpserver;
    requires jdk.jdi;

    exports software.amazon.smithy.java.client.http.netty;
    exports software.amazon.smithy.java.client.http.netty.h2;

    provides software.amazon.smithy.java.client.core.ClientTransportFactory
            with software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport.Factory;
}
