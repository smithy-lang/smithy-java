/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.serde.SerializationException;

public class HttpLabelSerializerTest {
    @Test
    public void doesNotAllowEmptyLabels() {
        Map<String, String> labels = new HashMap<>();
        HttpLabelSerializer labelSerializer = new HttpLabelSerializer(labels::put);

        var e = Assertions.assertThrows(SerializationException.class, () -> {
            labelSerializer.writeString(PreludeSchemas.DOCUMENT, "");
        });

        assertThat(e.getMessage(), containsString("HTTP label for `smithy.api#Document` cannot be empty"));
    }
}
