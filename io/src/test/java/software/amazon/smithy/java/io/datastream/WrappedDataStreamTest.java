/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
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
}
