/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.PaginatedTrait;

final class PaginatedTraitInitializer implements TraitInitializer<PaginatedTrait> {
    @Override
    public Class<PaginatedTrait> traitClass() {
        return PaginatedTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, PaginatedTrait paginatedTrait) {
        writer.putContext("input", paginatedTrait.getInputToken());
        writer.putContext("output", paginatedTrait.getOutputToken());
        writer.putContext("pageSize", paginatedTrait.getPageSize());
        writer.putContext("items", paginatedTrait.getItems());
        writer.putContext("paginated", PaginatedTrait.class);
        writer.writeInline(
                "${paginated:T}.builder()"
                        + "${?input}.inputToken(${input:S})${/input}"
                        + "${?output}.outputToken(${output:S})${/output}"
                        + "${?items}.items(${items:S})${/items}"
                        + "${?pageSize}.pageSize(${pageSize:S})${/pageSize}"
                        + ".build()");
    }
}
