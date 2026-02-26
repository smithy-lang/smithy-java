/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

class DefaultEventStreamReaderTest {

    static final String[] SOURCES = {
            """
                    O thou my lovely boy, who in thy pow'r
                    Dost hold time's fickle glass, his fickle hour,
                    Who hast by waning grown, and therein show'st,
                    Thy lover's with'ring, as thy sweet self grow'st,
                    If nature (sov'reign mistress over wrack)
                    As thou go'st onwards still will pluck thee back,
                    She keeps thee to this purpose, that her skill
                    May time disgrace, and wretched minute kill.
                    Yet fear her, O thou minion of her pleasure,
                    She may detain but not still keep her treasure!
                    Her audit (though delay'd) answer'd must be,
                    And her quietus is to render thee.
                    """,
            """
                    Es hielo abrasador, es fuego helado,
                    es herida que duele y no se siente,
                    es un soñado bien, un mal presente,
                    es un breve descanso muy cansado.
                    Es un descuido que nos da cuidado,
                    un cobarde, con nombre de valiente,
                    un andar solitario entre la gente,
                    un amar solamente ser amado.
                    Es una libertad encarcelada,
                    que dura hasta el postrero parasismo,
                    enfermedad que crece si es curada.
                    Este es el niño Amor, este es su abismo.
                    ¡Mirad cuál amistad tendrá con nada
                    el que en todo es contrario de sí mismo!
                    """,
            """
                    太乙近天都，
                    连山到海隅。
                    白云回望合，
                    青霭入看无。
                    分野中峰变，
                    阴晴众壑殊。
                    欲投人处宿，
                    隔水问樵夫。
                    """,
            """
                    من آن ملّای رومی‌ام که از نظمم شکر خیزد
                    """
    };

    @Test
    @SuppressWarnings("resource")
    public void testDecode() throws Exception {
        var writer = createWriter();
        var reader = createReader(writer.toDataStream());
        var result = new ArrayList<TestMessage.TestEvent>();

        // Act
        var writeWorker = Thread.ofVirtual().start(() -> {
            for (String msg : SOURCES) {
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
        var expected = List.of(SOURCES);
        var actual = result.stream().map(Object::toString).toList();
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("resource")
    public void testMultipleReadersCloseIndependently() throws Exception {
        // Regression test: inputClosed was static, so closing one reader
        // prevented all subsequent readers from closing their input streams.
        var writer1 = createWriter();
        var reader1 = createReader(writer1.toDataStream());
        Thread.ofVirtual().start(() -> {
            writer1.write(new TestMessage.TestEvent("event1"));
            writer1.close();
        });
        var event1 = reader1.read();
        assertEquals("event1", event1.toString());
        reader1.close();

        // Second reader should work independently
        var writer2 = createWriter();
        var reader2 = createReader(writer2.toDataStream());
        Thread.ofVirtual().start(() -> {
            writer2.write(new TestMessage.TestEvent("event2"));
            writer2.close();
        });
        var event2 = reader2.read();
        assertEquals("event2", event2.toString());
        // close should succeed, not be blocked by the first reader's close
        reader2.close();
        // Verify the reader is actually in CLOSED state
        assertThrows(IllegalStateException.class, reader2::read);
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
        writer.bootstrap(new TestMessage.TestEventEncoderFactory(), null);
        return writer;
    }
}
