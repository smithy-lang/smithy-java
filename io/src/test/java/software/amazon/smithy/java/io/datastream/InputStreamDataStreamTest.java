/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InputStreamDataStreamTest {
    @Test
    public void createsInputStreamDataStream() throws Exception {
        var ds = DataStream.ofInputStream(Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())));

        assertThat(ds.contentLength(), equalTo(-1L));
        assertThat(ds.contentType(), nullValue());
        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
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
        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void convertsToInputStream() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        assertThat(ds.asInputStream().readAllBytes(), equalTo("Hello!".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsDataToByteBuffer() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        assertThat(ds.asByteBuffer(), equalTo(ByteBuffer.wrap("Hello!".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void cannotReadDataStreamTwice() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())),
                "text/plain",
                6);

        Assertions.assertDoesNotThrow(ds::asInputStream);
        Assertions.assertThrows(IllegalStateException.class, ds::asInputStream);
    }

    @Test
    public void isAvailableBeforeConsumption() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())));

        assertThat(ds.isAvailable(), is(true));
    }

    @Test
    public void isNotAvailableAfterConsumption() throws Exception {
        var ds = DataStream.ofInputStream(
                Files.newInputStream(Paths.get(getClass().getResource("test.txt").toURI())));

        ds.asInputStream();
        assertThat(ds.isAvailable(), is(false));
    }

    @Test
    public void writeTo() throws IOException {
        var data = "from input stream".getBytes(StandardCharsets.UTF_8);
        var ds = DataStream.ofInputStream(new ByteArrayInputStream(data));
        var out = new ByteArrayOutputStream();

        ds.writeTo(out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void writeToNotReplayable() throws IOException {
        var ds = DataStream.ofInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        ds.writeTo(new ByteArrayOutputStream());

        assertThrows(IllegalStateException.class, () -> ds.writeTo(new ByteArrayOutputStream()));
    }
}
