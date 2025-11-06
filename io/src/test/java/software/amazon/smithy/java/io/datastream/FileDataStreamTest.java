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
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class FileDataStreamTest {
    @Test
    public void createsFromFile() throws Exception {
        var ds = DataStream.ofFile(Paths.get(getClass().getResource("test.txt").toURI()));

        assertThat(ds.contentLength(), equalTo(6L));
        assertThat(ds.contentType(), equalTo("text/plain"));
        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
        assertThat(ds.isReplayable(), is(true));
    }

    @Test
    public void createsFromFileWithMetadata() throws Exception {
        var ds = DataStream.ofFile(Paths.get(getClass().getResource("test.txt").toURI()), "text/foo");

        assertThat(ds.contentLength(), equalTo(6L));
        assertThat(ds.contentType(), equalTo("text/foo"));
        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void convertsFileStreamToInputStream() throws Exception {
        var ds = DataStream.ofFile(Paths.get(getClass().getResource("test.txt").toURI()), "text/foo");

        assertThat(ds.asInputStream().readAllBytes(), equalTo("Hello!".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsDataToByteBuffer() throws Exception {
        var ds = DataStream.ofFile(Paths.get(getClass().getResource("test.txt").toURI()));

        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }
}
