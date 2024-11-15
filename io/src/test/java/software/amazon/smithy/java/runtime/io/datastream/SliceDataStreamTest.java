/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.io.Slice;

public class SliceDataStreamTest {
    @Test
    public void createsDataStream() throws Exception {
        var bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var slice = new Slice(bytes, 1, 3);
        var ds = DataStream.ofSlice(slice);

        assertThat(ds.waitForSlice(), sameInstance(slice));
        assertThat(ds.asSlice().get(), sameInstance(slice));
        assertThat(ds.contentLength(), is(3L));
        assertThat(new Slice(ds.waitForByteBuffer()).bytes(), is(bytes));
        assertThat(ds.waitForByteBuffer().remaining(), is(3));
    }
}
