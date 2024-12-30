/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class InputStreamDataStreamTest {
    @Test
    public void createsInputStreamDataStream() throws Exception {
        var ds = DataStream.ofInputStream(Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())));

        assertThat(ds.contentLength(), equalTo(-1L));
        assertThat(ds.contentType(), nullValue());
        assertThat(ds.asByteBuffer().get(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
        assertThat(ds.isReplayable(), is(false));
    }

    @Test
    public void createsInputStreamDataStreamWithMetadata() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        assertThat(ds.contentLength(), equalTo(6L));
        assertThat(ds.contentType(), equalTo("text/plain"));
        assertThat(ds.asByteBuffer().get(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void convertsToInputStream() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        assertThat(ds.asInputStream().get().readAllBytes(), equalTo("Hello!".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsDataToByteBuffer() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        assertThat(ds.waitForByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }
}
