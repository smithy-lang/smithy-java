/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;

/**
 * Fuzz test for H2 frame codec — feeds random bytes as a stream of H2 frames.
 */
class H2FrameCodecFuzzTest {

    private static final int MAX_FUZZ_INPUT = 512;

    @FuzzTest
    void fuzzFrameStream(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        var codec = new H2FrameCodec(
                new UnsyncBufferedInputStream(new ByteArrayInputStream(data), 1024),
                new UnsyncBufferedOutputStream(new ByteArrayOutputStream(), 1024),
                16384);
        for (int i = 0; i < 10; i++) {
            try {
                codec.nextFrame();
                int length = codec.framePayloadLength();
                if (length > 0) {
                    byte[] payload = new byte[length];
                    codec.readPayloadInto(payload, 0, length);
                }
            } catch (IOException ignored) {
                break;
            }
        }
    }
}
