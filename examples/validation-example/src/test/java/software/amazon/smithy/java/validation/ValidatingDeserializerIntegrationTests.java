/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.ValidationError;
import software.amazon.smithy.java.core.schema.Validator;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.validation.example.model.Person;

class ValidatingDeserializerIntegrationTests {

    private final JsonCodec codec = JsonCodec.builder().build();

    @Test
    void basicTest() {
        var errors = validate("""
                {
                    "age" : 151
                }
                """, Person.builder());
        assertThat(errors).hasSize(2);

        var error1 = errors.get(0);
        assertThat(error1.path()).isEqualTo("/age");
        assertThat(error1.message()).isEqualTo("Value must be less than or equal to 150");

        var error2 = errors.get(1);
        assertThat(error2.path()).isEqualTo("/");
        assertThat(error2.message()).isEqualTo("Value missing required member: name");
    }

    @Test
    void stopsWhenMaxErrorsReached() {
        var errors = validate("""
                {
                    "age" : 151
                }
                """, Person.builder(), Validator.builder().maxAllowedErrors(1).maxDepth(10).build());
        assertThat(errors).hasSize(1);
    }

    @Test
    void stopsWhenStructureIsTooDeep() {
        var nestedPayload = """
                {
                  "parents": {
                    "father": {
                      "parents": {
                        "father": {
                          "parents": {
                            "father": {
                              "name": "4"
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        // Run validation with a maxDepth of 3.
        // This should produce an error once we go beyond 3 nesting levels.
        var errors = validate(nestedPayload, Person.builder(), Validator.builder().maxDepth(3).build());

        // We expect exactly 1 error complaining about exceeding the maximum depth.
        assertThat(errors).hasSize(1);

        var error = errors.get(0);
        // Depending on how your validation library reports the path, it might stop at the point where
        // it detected the structure was too deep. Adjust accordingly.
        assertThat(error.path()).isEqualTo("/parents/father/parents/father/parents");
        assertThat(error.message()).contains("exceeded maximum depth of 3");
    }

    private <T extends SerializableStruct> List<ValidationError> validate(
            String payload,
            ShapeBuilder<T> builder
    ) {
        return validate(payload, builder, Validator.builder().build());
    }

    private <T extends SerializableStruct> List<ValidationError> validate(
            String payload,
            ShapeBuilder<T> builder,
            Validator validator
    ) {
        var deser = codec.createDeserializer(payload.getBytes(StandardCharsets.UTF_8));
        return validator.deserializeAndValidate(builder, deser);
    }
}
