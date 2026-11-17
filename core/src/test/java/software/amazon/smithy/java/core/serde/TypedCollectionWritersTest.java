/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture.Options;

public class TypedCollectionWritersTest {

    private static String serialize(SerializableStruct struct, UnaryOperator<ShapeSerializer> wrap) {
        var delegate = new ToStringSerializer();
        wrap.apply(delegate).writeStruct(struct.schema(), struct);
        return delegate.toString();
    }

    @Test
    public void defaultWritersMatchGenericWriters() {
        var typed = ToStringSerializer.serialize(CollectionWriterFixture.typed(Options.ALL));
        var generic = ToStringSerializer.serialize(CollectionWriterFixture.generic(Options.ALL));

        assertThat(typed, equalTo(generic));
        assertThat(typed, containsString("stringList"));
        assertThat(typed, containsString("sparseStructMap"));
        assertThat(typed, containsString("nestedMap"));
    }

    @Test
    public void interceptingSerializerForwardsTypedWriters() {
        var typed = serialize(CollectionWriterFixture.typed(Options.ALL), delegate -> new InterceptingSerializer() {
            @Override
            protected ShapeSerializer before(Schema schema) {
                return delegate;
            }
        });
        var generic = ToStringSerializer.serialize(CollectionWriterFixture.generic(Options.ALL));

        assertThat(typed, equalTo(generic));
    }

    @Test
    public void listSerializerForwardsTypedWriters() {
        var typed = serialize(CollectionWriterFixture.typed(Options.ALL),
                delegate -> new ListSerializer(delegate, i -> {}));
        var generic = ToStringSerializer.serialize(CollectionWriterFixture.generic(Options.ALL));

        assertThat(typed, equalTo(generic));
    }
}
