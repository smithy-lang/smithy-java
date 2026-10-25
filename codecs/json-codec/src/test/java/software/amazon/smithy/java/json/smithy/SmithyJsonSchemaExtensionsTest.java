/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.JsonNameTrait;

final class SmithyJsonSchemaExtensionsTest {
    @Test
    void precomputesEscapedFieldTokens() {
        Schema schema = Schema.structureBuilder(ShapeId.from("example#EscapedNames"))
                .putMember(
                        "value",
                        PreludeSchemas.STRING,
                        new JsonNameTrait("quoted\"slash\\line\n"))
                .build();

        var memberExtension = schema.member("value").getExtension(SmithyJsonSchemaExtensions.KEY);
        var structureExtension = schema.getExtension(SmithyJsonSchemaExtensions.KEY);

        assertThat(new String(memberExtension.jsonNameBytes(), StandardCharsets.UTF_8))
                .isEqualTo("\"quoted\\\"slash\\\\line\\n\":");
        assertThat(structureExtension.jsonFieldNameTable()[0])
                .containsExactly(memberExtension.jsonNameBytes());
    }
}
