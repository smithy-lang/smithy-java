/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Verifies that double/float serialization via the Smithy JSON serializer produces
 * output that roundtrips correctly through {@link Double#parseDouble}/{@link Float#parseFloat}.
 * The Smithy serializer delegates to the Schubfach algorithm for non-integer finite values.
 */
class SchubfachTest {

    private static final SmithyJsonSerdeProvider SMITHY = new SmithyJsonSerdeProvider();

    private String serializeDouble(double v) throws Exception {
        try (var codec = JsonCodec.builder().overrideSerdeProvider(SMITHY).build();
                var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeDouble(PreludeSchemas.DOUBLE, v);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String serializeFloat(float v) throws Exception {
        try (var codec = JsonCodec.builder().overrideSerdeProvider(SMITHY).build();
                var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeFloat(PreludeSchemas.FLOAT, v);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0,
            1.0,
            -1.0,
            42.0,
            3.14,
            -3.14,
            0.1,
            1e100,
            1e-100,
            1.7976931348623157E308,
            4.9E-324})
    void doubleRoundtripsCorrectly(double v) throws Exception {
        assertThat(Double.parseDouble(serializeDouble(v)), equalTo(v));
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0f,
            1.0f,
            -1.0f,
            42.0f,
            3.14f,
            -3.14f,
            0.1f,
            1e10f,
            1e-10f,
            Float.MAX_VALUE,
            Float.MIN_VALUE})
    void floatRoundtripsCorrectly(float v) throws Exception {
        assertThat(Float.parseFloat(serializeFloat(v)), equalTo(v));
    }
}
