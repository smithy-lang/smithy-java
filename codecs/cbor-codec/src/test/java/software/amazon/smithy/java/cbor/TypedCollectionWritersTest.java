/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture.Options;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class TypedCollectionWritersTest {

    @Test
    public void typedWritersMatchGenericWriters() {
        var codec = Rpcv2CborCodec.builder().build();
        var typed = ByteBufferUtils.getBytes(codec.serialize(CollectionWriterFixture.typed(Options.ALL)));
        var generic = ByteBufferUtils.getBytes(codec.serialize(CollectionWriterFixture.generic(Options.ALL)));

        assertArrayEquals(generic, typed);
        var document = codec.createDeserializer(typed).readDocument();
        assertEquals(2, document.getMember("stringList").asList().size());
        assertEquals(2, document.getMember("sparseStructMap").asStringMap().size());
    }
}
