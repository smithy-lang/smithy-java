/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.io.datastream.DataStream;

class BodiesTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainsPooledStorageWithoutEscapingOrReleasingIt(boolean responsePayload) {
        byte[] storage = new byte[20_000];
        var recycled = new ArrayList<byte[]>();
        var body = DataStream.ofLeasedBytes(storage, 7, 19_000, null, recycled::add);

        long count = responsePayload ? Bodies.drainPayload(body) : Bodies.drain(body);

        assertThat(count).isEqualTo(19_000);
        assertThat(body.escaped()).isFalse();
        assertThat(body.isReleased()).isFalse();
        assertThat(recycled).isEmpty();
        body.release();
        assertThat(recycled).containsExactly(storage);
    }
}
