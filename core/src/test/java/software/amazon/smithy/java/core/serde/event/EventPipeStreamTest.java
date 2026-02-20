/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventPipeStreamTest {
    public static final String[] SOURCES = {
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

    @ParameterizedTest
    @MethodSource("sources")
    void testEventInputStream(String source) throws IOException {
        var eventInputStream = new EventPipeStream();
        Thread.ofVirtual().start(() -> {
            for (var idx = 0; idx < source.length(); idx++) {
                eventInputStream.write(ByteBuffer.wrap(charToUtf8Bytes(source.charAt(idx))));
            }
            eventInputStream.complete();
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        eventInputStream.transferTo(out);
        assertEquals(source, out.toString());
    }

    static String[] sources() {
        return SOURCES;
    }

    static byte[] charToUtf8Bytes(char c) {
        return Character.toString(c).getBytes(StandardCharsets.UTF_8);
    }
}
