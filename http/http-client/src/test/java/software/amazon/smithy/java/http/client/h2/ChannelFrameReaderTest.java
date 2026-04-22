/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ChannelFrameReaderTest {

    @Test
    void hasBufferedDataIncludesTransportBufferedPlaintext() {
        AtomicBoolean transportBuffered = new AtomicBoolean(false);
        ChannelFrameReader reader = new ChannelFrameReader(
                java.nio.channels.Channels.newChannel(new ByteArrayInputStream(new byte[0])),
                8,
                transportBuffered::get);

        assertFalse(reader.hasBufferedData());

        transportBuffered.set(true);

        assertTrue(reader.hasBufferedData());
    }

    @Test
    void hasBufferedDataIncludesReaderBuffer() throws Exception {
        ChannelFrameReader reader = new ChannelFrameReader(
                java.nio.channels.Channels.newChannel(new ByteArrayInputStream(new byte[] {1})),
                8);

        reader.ensure(1);

        assertTrue(reader.hasBufferedData());
    }
}
