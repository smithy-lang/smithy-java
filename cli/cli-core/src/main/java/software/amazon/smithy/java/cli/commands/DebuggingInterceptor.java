/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.commands;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.ByteBufferUtils;


/**
 * Prints out the wire request/response for debugging.
 *
 * <p>Formatting is intended to be similar to curl debug.
 */
// TODO: Support other protocols
record DebuggingInterceptor(ColorFormatter formatter, CliPrinter printer)
        implements ClientInterceptor {

    @Override
    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
        if (hook.request() instanceof HttpRequest sr) {
            try (var buffer = ColorBuffer.of(formatter, printer)) {
                buffer.println("* Trying request to: " + sr.headers().firstValue("Host"));
                sr.toString().lines().forEach(s -> buffer.println("> " + s));
                if (sr.body().hasKnownLength()) {
                    new String(ByteBufferUtils.getBytes(sr.body().waitForByteBuffer()), StandardCharsets.UTF_8)
                            .lines()
                            .forEach(s -> buffer.println("> " + s));
                }
            }
        }
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
        try (var buffer = ColorBuffer.of(formatter, printer)) {
            buffer.println("* Request sent");
        }
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
        if (hook.response() instanceof HttpResponse sr) {
            try (var buffer = ColorBuffer.of(formatter, printer)) {
                buffer.println("* Received response");
                sr.toString().lines().forEach(s -> buffer.println("< " + s));
                if (sr.body().hasKnownLength()) {
                    new String(ByteBufferUtils.getBytes(sr.body().waitForByteBuffer()), StandardCharsets.UTF_8)
                            .lines()
                            .forEach(s -> buffer.println("< " + s));
                }
            }
        }
    }
}
