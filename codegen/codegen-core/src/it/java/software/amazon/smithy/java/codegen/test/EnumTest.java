/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.EnumType;
import software.amazon.smithy.java.json.JsonCodec;

public class EnumTest {

    @Test
    void unknownTypeDeserializedIntoUnknownVariant() {
        EnumType output;
        try (var codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build()) {
            output = codec.deserializeShape("\"option-n\"", EnumType.builder());
        }
        switch (output) {
            case EnumType.$Unknown(String value) -> assertEquals("option-n", value);
            default -> fail("Expected UnknownEnumType");
        }
    }
}
