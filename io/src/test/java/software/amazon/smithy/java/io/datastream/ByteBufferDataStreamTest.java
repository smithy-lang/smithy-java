/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ByteBufferDataStreamTest {
    @Test
    public void returnsByteBuffer() {
        var bytes = "foo".getBytes(StandardCharsets.UTF_8);
        var ds = DataStream.ofBytes(bytes);

        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8))));
        assertThat(ds.isReplayable(), is(true));
    }

    @Test
    public void isAlwaysAvailable() {
        var ds = DataStream.ofBytes("foo".getBytes(StandardCharsets.UTF_8));

        assertThat(ds.isAvailable(), is(true));
        ds.asByteBuffer();
        assertThat(ds.isAvailable(), is(true));
    }

    @Test
    public void writeTo() throws IOException {
        var data = "hello world".getBytes(StandardCharsets.UTF_8);
        var ds = DataStream.ofBytes(data);
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void writeToIsReplayable() throws IOException {
        var data = "replay".getBytes(StandardCharsets.UTF_8);
        var ds = DataStream.ofBytes(data);

        var out1 = new ByteArrayOutputStream();
        ds.writeTo(out1);
        var out2 = new ByteArrayOutputStream();
        ds.writeTo(out2);

        assertArrayEquals(data, out1.toByteArray());
        assertArrayEquals(data, out2.toByteArray());
    }
}
