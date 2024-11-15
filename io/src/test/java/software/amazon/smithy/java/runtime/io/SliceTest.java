/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SliceTest {
    @Test
    public void wrapsBytes() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var slice = new Slice(bytes);

        assertThat(slice.bytes(), sameInstance(bytes));
        assertThat(slice.offset(), is(0));
        assertThat(slice.length(), is(bytes.length));
    }

    @Test
    public void wrapsByteBuffer() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var bb = ByteBuffer.wrap(bytes);
        var slice = new Slice(bb);

        assertThat(slice.bytes(), sameInstance(bytes));
        assertThat(slice.offset(), is(0));
        assertThat(slice.length(), is(bytes.length));
    }

    @Test
    public void wrapsByteBufferAtOffset() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var bb = ByteBuffer.wrap(bytes, 1, 3);
        var slice = new Slice(bb);

        assertThat(bb.position(), is(1));
        assertThat(bb.remaining(), is(3));

        assertThat(slice.bytes(), sameInstance(bytes));
        assertThat(slice.offset(), is(1));
        assertThat(slice.length(), is(3));

        assertThat(slice.at(0), equalTo((byte) 'e'));
        assertThat(slice.at(1), equalTo((byte) 'l'));
        assertThat(slice.at(2), equalTo((byte) 'l'));

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> slice.at(3));
    }

    @Test
    public void doesNotAllowOobAccess() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var bb = ByteBuffer.wrap(bytes, 1, 2);
        var slice = new Slice(bb);

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> slice.at(3));
    }

    @Test
    public void convertsToBbWithNoOffset() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var bb = ByteBuffer.wrap(bytes);
        var slice = new Slice(bb);
        var roundTrip = slice.toByteBuffer();

        System.out.println(bb.limit());

        assertThat(roundTrip.position(), equalTo(bb.position()));
        assertThat(roundTrip.limit(), equalTo(bb.limit()));
        assertThat(roundTrip.array(), is(bytes));
        assertThat(roundTrip.array(), is(bb.array()));
    }

    @Test
    public void convertsToBbWithOffset() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var bb = ByteBuffer.wrap(bytes, 1, 3);
        var slice = new Slice(bb);
        var roundTrip = slice.toByteBuffer();

        assertThat(roundTrip.position(), equalTo(bb.position()));
        assertThat(roundTrip.limit(), equalTo(bb.limit()));
        assertThat(roundTrip.array(), is(bytes));
        assertThat(roundTrip.array(), is(bb.array()));
    }

    @Test
    public void copiesSlice() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var slice = new Slice(bytes);

        assertThat(Arrays.equals(bytes, slice.copyBytes()), is(true));
    }

    @Test
    public void copiesSliceSubset() {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var slice = new Slice(bytes, 1, 3);
        var copy = slice.copyBytes();

        assertThat(copy.length, is(3));
        assertThat(copy[0], equalTo((byte) 'e'));
        assertThat(copy[1], equalTo((byte) 'l'));
        assertThat(copy[2], equalTo((byte) 'l'));
    }
}
