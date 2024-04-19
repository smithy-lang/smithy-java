/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;

public class ListSerializerTest {
    @Test
    public void incrementsPosition() {
        List<Integer> positions = new ArrayList<>();
        List<String> strings = new ArrayList<>();

        var delegate = new SpecificShapeSerializer() {
            @Override
            public void writeString(Schema schema, String value) {
                strings.add(value);
            }
        };

        ListSerializer listSerializer = new ListSerializer(delegate, positions::add);

        listSerializer.writeString(PreludeSchemas.STRING, "1");
        listSerializer.writeString(PreludeSchemas.STRING, "2");

        assertThat(positions, contains(0, 1));
        assertThat(strings, contains("1", "2"));
        assertThat(listSerializer.position(), is(2));
    }
}
