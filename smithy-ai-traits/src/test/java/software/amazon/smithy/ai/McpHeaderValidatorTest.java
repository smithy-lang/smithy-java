/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.validation.ValidatedResult;

class McpHeaderValidatorTest {

    @Test
    void acceptsStringMembers() {
        var result = assemble("/mcp-header-valid.smithy");

        assertEquals(0,
                result.getValidationEvents()
                        .stream()
                        .filter(event -> event.getMessage().contains("mcpHeader trait can only"))
                        .count());
    }

    @Test
    void rejectsNonStringMembers() {
        var result = assemble("/mcp-header-invalid.smithy");

        assertEquals(1,
                result.getValidationEvents()
                        .stream()
                        .filter(event -> event.getMessage().contains("mcpHeader trait can only"))
                        .count());
    }

    private ValidatedResult<Model> assemble(String resource) {
        return Model.assembler()
                .addImport(Objects.requireNonNull(getClass().getResource(resource)))
                .discoverModels(getClass().getClassLoader())
                .assemble();
    }
}
