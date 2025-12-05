/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class FailingOutputStreamTest {

    @Test
    void writeThrowsConfiguredException() {
        var expected = new IOException("test error");
        var stream = new FailingOutputStream(expected);
        var thrown = assertThrows(IOException.class, () -> stream.write(1));

        assertSame(expected, thrown);
    }

    @Test
    void flushThrowsConfiguredException() {
        var expected = new IOException("test error");
        var stream = new FailingOutputStream(expected);
        var thrown = assertThrows(IOException.class, stream::flush);

        assertSame(expected, thrown);
    }

    @Test
    void closeThrowsConfiguredException() {
        var expected = new IOException("test error");
        var stream = new FailingOutputStream(expected);
        var thrown = assertThrows(IOException.class, stream::close);

        assertSame(expected, thrown);
    }
}
