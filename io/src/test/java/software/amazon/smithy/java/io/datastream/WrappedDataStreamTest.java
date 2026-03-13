/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class WrappedDataStreamTest {
    @Test
    public void returnsByteBuffer() {
        var bytes = "foo".getBytes(StandardCharsets.UTF_8);
        var ds = DataStream.ofBytes(bytes);
        var wrapped = DataStream.ofPublisher(ds, "text/plain", 3);

        assertThat(wrapped.asByteBuffer(), equalTo(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void delegatesIsAvailableToUnderlyingStream() {
        var ds = DataStream.ofInputStream(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)));
        var wrapped = DataStream.withMetadata(ds, "text/plain", 3L, null);

        assertThat(wrapped.isAvailable(), is(true));
        ds.asInputStream();
        assertThat(wrapped.isAvailable(), is(false));
    }

    @Test
    public void writeToDelegates() throws IOException {
        var data = "wrapped".getBytes(StandardCharsets.UTF_8);
        var inner = DataStream.ofBytes(data);
        var ds = DataStream.withMetadata(inner, "text/plain", (long) data.length, true);
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals(data, out.toByteArray());
    }
}
