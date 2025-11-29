/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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
}
