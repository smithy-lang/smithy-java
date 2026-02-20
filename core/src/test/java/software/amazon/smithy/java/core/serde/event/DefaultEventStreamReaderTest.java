/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

class DefaultEventStreamReaderTest {

    @Test
    @SuppressWarnings("resource")
    public void testDecode() throws Exception {
        var writer = createWriter();
        var reader = createReader(writer.toDataStream());
        var result = new ArrayList<TestMessage.TestEvent>();

        // Act
        var writeWorker = Thread.ofVirtual().start(() -> {
            for (String msg : EventPipeStreamTest.sources()) {
                var event = new TestMessage.TestEvent(msg);
                writer.write(event);
            }
            writer.close();
        });
        var readWorker = Thread.ofVirtual().start(() -> {
            reader.forEach(result::add);
        });
        writeWorker.join();
        readWorker.join();

        // Assert
        var expected = List.of(EventPipeStreamTest.sources());
        var actual = result.stream().map(Object::toString).toList();
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);
    }

    static DefaultEventStreamReader<TestMessage.TestEvent, TestMessage.TestEvent, TestMessage.TestFrame> createReader(
            DataStream stream
    ) {
        return new DefaultEventStreamReader<>(stream, new TestMessage.TestEventDecoderFactory(), false);
    }

    static DefaultEventStreamWriter<TestMessage.TestEvent, TestMessage.TestEvent,
            TestMessage.TestFrame> createWriter() {
        var writer = new DefaultEventStreamWriter<TestMessage.TestEvent, TestMessage.TestEvent,
                TestMessage.TestFrame>();
        writer.bootstrap(new InternalEventStreamWriter.Bootstrap<>() {
            @Override
            public EventEncoderFactory<TestMessage.TestFrame> encoder() {
                return new TestMessage.TestEventEncoderFactory();
            }

            @Override
            public TestMessage.TestEvent initialEvent() {
                return null;
            }
        });
        return writer;
    }
}
