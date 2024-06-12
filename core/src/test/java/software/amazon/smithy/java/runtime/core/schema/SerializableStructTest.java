/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.testmodels.Bird;

public class SerializableStructTest {
    @Test
    public void filtersMembers() {
        var struct = Bird.builder().name("foo").build();
        var filtered = SerializableStruct.filteredMembers(Bird.SCHEMA, struct, member -> false);

        var serializer = new SpecificShapeSerializer() {
            private boolean wroteStruct;

            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                wroteStruct = true;
                struct.serializeMembers(this);
            }
        };

        // The filtered serializer doesn't serialize anything, so there's no exception.
        filtered.serialize(serializer);
        assertThat(serializer.wroteStruct, is(true));

        // If anything is serialized to the serializer, it'll throw.
        Assertions.assertThrows(RuntimeException.class, () -> struct.serialize(serializer));
    }
}
